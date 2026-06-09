# Camel 4.21 Header Rename 系统详解

## 概览

这个系统用于自动化迁移 Camel 4.21 中的 header 名称变更。它使用 OpenRewrite 框架来修改代码，支持 4 种不同的 DSL 格式。

## 架构层次

```
4.21.yaml (YAML 配置文件)
    ↓
RenameHeaders (协调器 Recipe)
    ↓
为每个 header 映射创建一个子 Recipe
    ↓
每个子 Recipe 包含 4 个 DSL-specific recipes:
    - RenameHeaderInJavaMethod      (Java 方法调用)
    - RenameHeaderInSimpleExpression (Simple 表达式)
    - RenameHeaderInXmlDsl           (XML DSL)
    - RenameHeaderInYamlDsl          (YAML DSL)
```

---

## 第一层：YAML 配置 (4.21.yaml)

### 作用
定义哪些 headers 需要迁移，并设置前置条件（preconditions）确保只在相关项目中运行。

### 示例：Kafka Headers
```yaml
type: specs.openrewrite.org/v1beta/recipe
name: org.apache.camel.upgrade.camel421.upgradeKafkaRecipes
displayName: Migrate camel-kafka headers
description: Renames Kafka header constants only if camel-kafka dependency is present.
preconditions:
  - org.openrewrite.java.dependencies.search.ModuleHasDependency:
      groupIdPattern: org.apache.camel
      artifactIdPattern: camel-kafka
recipeList:
  - org.apache.camel.upgrade.camel421.RenameHeaders:
      headerMappings:
        kafka.PARTITION_KEY: CamelKafkaPartitionKey
        kafka.PARTITION: CamelKafkaPartition
        kafka.KEY: CamelKafkaKey
        kafka.TOPIC: CamelKafkaTopic
        # ... 更多 headers
```

### 关键点
1. **preconditions** - 只有当项目依赖 `camel-kafka` 时才运行
2. **headerMappings** - 批量定义 header 映射关系
3. 一个 YAML entry 可以处理多个 headers

---

## 第二层：RenameHeaders (协调器)

### 文件
`camel-upgrade-recipes/src/main/java/org/apache/camel/upgrade/camel421/RenameHeaders.java`

### 作用
接收 header 映射，为每一对 (oldName, newName) 创建一个子 Recipe。

### 核心代码
```java
@Override
public List<Recipe> getRecipeList() {
    List<Recipe> recipes = new ArrayList<>();
    
    // 遍历所有的 header 映射
    if (headerMappings != null) {
        for (Map.Entry<String, String> entry : headerMappings.entrySet()) {
            recipes.add(createHeaderRenameRecipe(entry.getKey(), entry.getValue()));
        }
    }
    
    return recipes;
}

private Recipe createHeaderRenameRecipe(String oldName, String newName) {
    return new Recipe() {
        @Override
        public List<Recipe> getRecipeList() {
            return Arrays.asList(
                new RenameHeaderInJavaMethod(oldName, newName),
                new RenameHeaderInSimpleExpression(oldName, newName),
                new RenameHeaderInXmlDsl(oldName, newName),
                new RenameHeaderInYamlDsl(oldName, newName)
            );
        }
    };
}
```

### 示例：Kafka Headers
输入：
```yaml
headerMappings:
  kafka.TOPIC: CamelKafkaTopic
  kafka.KEY: CamelKafkaKey
```

输出：创建 2 个子 Recipes，每个包含 4 个 DSL-specific recipes（总共 8 个）。

---

## 第三层：DSL-Specific Recipes

### 1. RenameHeaderInJavaMethod

#### 处理的代码模式
```java
// setHeader 方法
exchange.getIn().setHeader("kafka.TOPIC", "topic1");

// getHeader 方法（多种重载）
String topic = exchange.getIn().getHeader("kafka.TOPIC");
String topic = exchange.getIn().getHeader("kafka.TOPIC", String.class);
String topic = exchange.getIn().getHeader("kafka.TOPIC", "default", String.class);
```

#### 工作原理
1. **方法匹配器**：定义需要处理的方法签名
   ```java
   private static final String MATCHER_SET_HEADER_2_ARGS = 
       "org.apache.camel.Message setHeader(String, Object)";
   private static final String MATCHER_GET_HEADER_1_ARG = 
       "org.apache.camel.Message getHeader(String)";
   // ... 更多重载
   ```

2. **访问者模式**：遍历 Java AST（抽象语法树）
   ```java
   @Override
   protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
       // 1. 检查是否是 setHeader/getHeader 方法
       if (matchesHeaderMethod(mi)) {
           // 2. 获取第一个参数（header 名称）
           if (mi.getArguments().get(0) instanceof J.Literal) {
               J.Literal literal = (J.Literal) mi.getArguments().get(0);
               
               // 3. 检查是否是字符串字面量且匹配 oldHeaderName
               if (oldHeaderName.equals(literal.getValue())) {
                   // 4. 替换为新的 header 名称
                   J.Literal newLiteral = literal.withValue(newHeaderName);
                   return mi.withArguments(newArgs);
               }
           }
       }
       return mi;
   }
   ```

3. **安全性**：只处理**字符串字面量**，不处理动态生成的 header 名称
   ```java
   // ✅ 会被处理
   setHeader("kafka.TOPIC", value)
   
   // ❌ 不会被处理（避免误报）
   String headerName = "kafka.TOPIC";
   setHeader(headerName, value)
   
   Map<String, Object> headers = new HashMap<>();
   headers.get("kafka.TOPIC")  // 不处理 Map 操作
   ```

---

### 2. RenameHeaderInSimpleExpression

#### 处理的代码模式
```java
// Simple 表达式中的 header 引用
.setBody(simple("${header.kafka.TOPIC}"))
.setBody(simple("${headers.kafka.KEY}"))
```

#### 工作原理
1. **正则表达式匹配**
   ```java
   // 匹配 ${header.oldName}
   this.headerPattern = Pattern.compile(
       "(\\$\\{header\\.)" + Pattern.quote(oldHeaderName) + "(\\})"
   );
   
   // 匹配 ${headers.oldName}
   this.headersPattern = Pattern.compile(
       "(\\$\\{headers\\.)" + Pattern.quote(oldHeaderName) + "(\\})"
   );
   ```

2. **只处理 simple() 方法调用**
   ```java
   @Override
   protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
       // 只处理名为 "simple" 的方法
       if (mi.getSimpleName().equals("simple")) {
           if (mi.getArguments().get(0) instanceof J.Literal) {
               String expression = (String) literal.getValue();
               
               // 使用正则替换
               String newExpression = headerPattern.matcher(expression)
                   .replaceAll("$1" + newHeaderName + "$2");
               
               return mi.withArguments(newArgs);
           }
       }
   }
   ```

3. **Pattern.quote() 的作用**
   ```java
   // 如果 oldHeaderName = "dns.name"
   Pattern.quote("dns.name")  // 返回 "\\Qdns.name\\E"
   
   // 这样 "." 不会被当作正则的"任意字符"，而是字面的点号
   ```

---

### 3. RenameHeaderInXmlDsl

#### 处理的代码模式
```xml
<!-- setHeader 元素 -->
<setHeader name="kafka.TOPIC">
    <constant>topic1</constant>
</setHeader>

<!-- header 元素 -->
<header name="kafka.KEY">...</header>

<!-- removeHeader 元素 -->
<removeHeader name="kafka.PARTITION"/>
```

#### 工作原理
1. **XML 标签访问者**
   ```java
   @Override
   public Xml.Tag doVisitTag(Xml.Tag tag, ExecutionContext ctx) {
       String tagName = t.getName();
       
       // 检查是否是 setHeader/header/removeHeader 标签
       if ("setHeader".equals(tagName) || 
           "header".equals(tagName) || 
           "removeHeader".equals(tagName)) {
           
           // 遍历属性
           return t.withAttributes(ListUtils.map(t.getAttributes(), attr -> {
               // 找到 name="kafka.TOPIC" 属性
               if ("name".equals(attr.getKeyAsString()) &&
                   oldHeaderName.equals(attr.getValueAsString())) {
                   
                   // 替换属性值
                   return attr.withValue(new Xml.Attribute.Value(
                       ..., newHeaderName
                   ));
               }
               return attr;
           }));
       }
       return t;
   }
   ```

---

### 4. RenameHeaderInYamlDsl

#### 处理的代码模式
```yaml
# setHeader
- setHeader:
    name: kafka.TOPIC
    constant: topic1

# header
- header:
    name: kafka.KEY

# removeHeader
- removeHeader:
    name: kafka.PARTITION
```

#### 工作原理
1. **YAML 映射条目访问者**
   ```java
   @Override
   public Yaml.Mapping.Entry doVisitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
       // 1. 检查 key 是否为 "name"
       if ("name".equals(e.getKey().getValue()) &&
           // 2. 值是否为 oldHeaderName
           oldHeaderName.equals(((Yaml.Scalar) e.getValue()).getValue())) {
           
           // 3. 检查是否在 setHeader/header/removeHeader 块内
           if (isHeaderOperation()) {
               // 4. 替换值
               return e.withValue(scalarValue.withValue(newHeaderName));
           }
       }
       return e;
   }
   ```

2. **父级上下文检查**
   ```java
   private boolean isHeaderOperation() {
       // 向上遍历 YAML 树，最多 10 层
       for (int i = 0; i < 10; i++) {
           Object value = getCursor().getParent(i).getValue();
           if (value instanceof Yaml.Mapping) {
               Yaml.Mapping mapping = (Yaml.Mapping) value;
               // 检查是否有 setHeader/header/removeHeader 键
               boolean hasHeaderOp = mapping.getEntries().stream()
                   .anyMatch(e -> {
                       String key = e.getKey().getValue();
                       return "setHeader".equals(key) || 
                              "header".equals(key) || 
                              "removeHeader".equals(key);
                   });
               if (hasHeaderOp) return true;
           }
       }
       return false;
   }
   ```

---

## 完整流程示例

### 输入：用户的 Camel 项目

**pom.xml**
```xml
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-kafka</artifactId>
    <version>4.20</version>
</dependency>
```

**MyRoute.java**
```java
public class MyRoute extends RouteBuilder {
    public void configure() {
        from("direct:start")
            .process(exchange -> {
                exchange.getIn().setHeader("kafka.TOPIC", "orders");
            })
            .setBody(simple("Sending to ${header.kafka.TOPIC}"))
            .to("kafka:mybroker");
    }
}
```

**route.xml**
```xml
<route>
    <from uri="direct:start"/>
    <setHeader name="kafka.KEY">
        <constant>orderKey</constant>
    </setHeader>
    <to uri="kafka:mybroker"/>
</route>
```

**route.yaml**
```yaml
- route:
    from:
      uri: direct:start
    steps:
      - setHeader:
          name: kafka.PARTITION
          constant: 0
```

### 执行过程

1. **precondition 检查**
   - 检测到 `camel-kafka` 依赖 → ✅ 继续

2. **RenameHeaders 协调器**
   - 读取 headerMappings：
     - `kafka.TOPIC → CamelKafkaTopic`
     - `kafka.KEY → CamelKafkaKey`
     - `kafka.PARTITION → CamelKafkaPartition`
   - 为每个映射创建 4 个 recipes（共 12 个）

3. **各 DSL Recipe 执行**

   **RenameHeaderInJavaMethod**
   - 找到 `setHeader("kafka.TOPIC", "orders")`
   - 替换为 `setHeader("CamelKafkaTopic", "orders")`

   **RenameHeaderInSimpleExpression**
   - 找到 `simple("... ${header.kafka.TOPIC}")`
   - 替换为 `simple("... ${header.CamelKafkaTopic}")`

   **RenameHeaderInXmlDsl**
   - 找到 `<setHeader name="kafka.KEY">`
   - 替换为 `<setHeader name="CamelKafkaKey">`

   **RenameHeaderInYamlDsl**
   - 找到 `name: kafka.PARTITION`
   - 替换为 `name: CamelKafkaPartition`

### 输出：迁移后的代码

**MyRoute.java**
```java
public class MyRoute extends RouteBuilder {
    public void configure() {
        from("direct:start")
            .process(exchange -> {
                exchange.getIn().setHeader("CamelKafkaTopic", "orders");  // ✅ 已更新
            })
            .setBody(simple("Sending to ${header.CamelKafkaTopic}"))  // ✅ 已更新
            .to("kafka:mybroker");
    }
}
```

**route.xml**
```xml
<route>
    <from uri="direct:start"/>
    <setHeader name="CamelKafkaKey">  <!-- ✅ 已更新 -->
        <constant>orderKey</constant>
    </setHeader>
    <to uri="kafka:mybroker"/>
</route>
```

**route.yaml**
```yaml
- route:
    from:
      uri: direct:start
    steps:
      - setHeader:
          name: CamelKafkaPartition  # ✅ 已更新
          constant: 0
```

---

## 安全性和限制

### ✅ 会处理的情况
```java
// 字符串字面量
setHeader("kafka.TOPIC", value)
getHeader("kafka.TOPIC")

// Simple 表达式
simple("${header.kafka.TOPIC}")
```

```xml
<setHeader name="kafka.TOPIC">...</setHeader>
```

```yaml
- setHeader:
    name: kafka.TOPIC
```

### ❌ 不会处理的情况（避免误报）
```java
// 变量存储的 header 名称
String headerName = "kafka.TOPIC";
setHeader(headerName, value)

// 动态构建的名称
setHeader("kafka." + operation, value)

// Map 操作
Map<String, Object> headers = ...;
headers.get("kafka.TOPIC")  // 不是 Message.getHeader()

// 常量引用
setHeader(KafkaConstants.TOPIC, value)  // 不是字符串字面量
```

---

## OpenRewrite 核心概念

### 1. Recipe
- 一个转换规则（transformation rule）
- 可以包含子 recipes（composition）

### 2. Visitor
- 访问者模式，遍历代码树（AST）
- 不同的语言有不同的 visitor：
  - `JavaVisitor` - Java 代码
  - `XmlVisitor` - XML 文件
  - `YamlVisitor` - YAML 文件

### 3. AST (Abstract Syntax Tree)
- 代码的树形结构表示
- 示例：`setHeader("name", value)` 的 AST：
  ```
  MethodInvocation
  ├── method: setHeader
  ├── select: exchange.getIn()
  └── arguments:
      ├── Literal("name")
      └── Identifier(value)
  ```

### 4. Preconditions
- 在运行 recipe 前的检查条件
- 示例：`ModuleHasDependency` 检查是否有特定依赖

---

## 测试示例

### 单元测试结构
```java
@Test
void testKafkaHeadersMigration() {
    rewriteRun(
        // 1. 设置 Maven 项目（包含依赖）
        mavenProject("test-kafka",
            pomXml(CamelTestUtil.pomXmlWithDependency("camel-kafka", v4_20)),
            
            // 2. 提供输入代码
            java(
                """
                exchange.getIn().setHeader("kafka.TOPIC", "topic1");
                """,
                // 3. 期望的输出
                """
                exchange.getIn().setHeader("CamelKafkaTopic", "topic1");
                """
            )
        )
    );
}
```

### Precondition 测试
```java
@Test
void testPreconditionBlocksWithoutDependency() {
    rewriteRun(
        spec -> spec.expectedCyclesThatMakeChanges(0),  // 期望 0 次变更
        mavenProject("test-negative",
            // 使用 camel-core 而不是 camel-kafka
            pomXml(CamelTestUtil.pomXmlWithDependency("camel-core", v4_20)),
            java(
                """
                // 这段代码不会被修改，因为缺少 camel-kafka 依赖
                exchange.getIn().setHeader("kafka.TOPIC", "topic1");
                """
            )
        )
    );
}
```

---

## 总结

### 关键设计模式
1. **组合模式**：RenameHeaders 包含多个子 recipes
2. **访问者模式**：遍历不同类型的代码树
3. **策略模式**：每种 DSL 有独立的处理策略

### 优势
1. **安全性**：只处理明确的字符串字面量
2. **模块化**：每种 DSL 独立处理
3. **可扩展**：添加新组件只需在 YAML 中配置
4. **智能化**：通过 preconditions 避免误报

### 扩展方法
添加新组件的 header migration：
1. 在 `4.21.yaml` 中添加新的 recipe entry
2. 定义 precondition（检查依赖）
3. 在 `headerMappings` 中列出所有 headers
4. 在 `CamelUpdate421Test.java` 中添加测试

无需修改任何 Java 代码！
