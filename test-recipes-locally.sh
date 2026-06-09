#!/bin/bash
set -e

echo "========================================="
echo "Testing Camel 4.21 Migration Recipes"
echo "========================================="

# Step 1: Build and install recipes
echo ""
echo "Step 1: Building and installing recipes to local Maven repository..."
mvn clean install -DskipTests

# Step 2: Create test project
echo ""
echo "Step 2: Creating test project..."
TEST_DIR="/tmp/camel-421-migration-test-$$"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

# Initialize git for tracking changes
git init
git config user.email "test@test.com"
git config user.name "Test User"

# Create pom.xml
cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.test</groupId>
    <artifactId>camel-421-test</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <camel.version>4.20.0</camel.version>
    </properties>

    <dependencies>
        <!-- Components to be removed -->
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-stomp</artifactId>
            <version>${camel.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-aws-xray</artifactId>
            <version>${camel.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-guava-eventbus</artifactId>
            <version>${camel.version}</version>
        </dependency>

        <!-- Dependencies to be changed -->
        <dependency>
            <groupId>io.krakens</groupId>
            <artifactId>java-grok</artifactId>
            <version>0.1.9</version>
        </dependency>
        <dependency>
            <groupId>commons-dbcp</groupId>
            <artifactId>commons-dbcp</artifactId>
            <version>1.4</version>
        </dependency>

        <!-- Components with header renames -->
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-kafka</artifactId>
            <version>${camel.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-jgroups</artifactId>
            <version>${camel.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-core</artifactId>
            <version>${camel.version}</version>
        </dependency>
    </dependencies>
</project>
EOF

# Create Java source files
mkdir -p src/main/java/com/test
cat > src/main/java/com/test/MyKafkaRoute.java << 'EOF'
package com.test;

import org.apache.camel.builder.RouteBuilder;

public class MyKafkaRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("direct:start")
            .process(exchange -> {
                // Old Kafka headers - should be renamed
                exchange.getIn().setHeader("kafka.TOPIC", "myTopic");
                exchange.getIn().setHeader("kafka.KEY", "myKey");
                exchange.getIn().setHeader("kafka.PARTITION", 0);
            })
            .setBody(simple("Sending to ${header.kafka.TOPIC}"))
            .to("kafka:mybroker");
    }
}
EOF

cat > src/main/java/com/test/MyJGroupsRoute.java << 'EOF'
package com.test;

import org.apache.camel.builder.RouteBuilder;

public class MyJGroupsRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("direct:start")
            .process(exchange -> {
                // Old JGroups headers - should be renamed
                exchange.getIn().setHeader("JGROUPS_DEST", "destination");
                exchange.getIn().setHeader("JGROUPS_SRC", "source");
            })
            .to("jgroups:clusterName");
    }
}
EOF

# Commit initial state
git add .
git commit -m "Initial state before migration"

# Step 3: Run migration dry-run first
echo ""
echo "Step 3: Running migration (dry-run to preview changes)..."
mvn org.openrewrite.maven:rewrite-maven-plugin:dryRun \
  -Drewrite.recipeArtifactCoordinates=org.apache.camel.upgrade:camel-upgrade-recipes:LATEST \
  -Drewrite.activeRecipes=org.apache.camel.upgrade.camel421.CamelMigrationRecipe

# Show the patch
if [ -f target/rewrite/rewrite.patch ]; then
    echo ""
    echo "========================================="
    echo "Preview of changes (rewrite.patch):"
    echo "========================================="
    cat target/rewrite/rewrite.patch
fi

# Step 4: Apply migration
echo ""
echo "Step 4: Applying migration..."
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.apache.camel.upgrade:camel-upgrade-recipes:LATEST \
  -Drewrite.activeRecipes=org.apache.camel.upgrade.camel421.CamelMigrationRecipe

# Step 5: Show changes
echo ""
echo "========================================="
echo "Git Diff - All Changes:"
echo "========================================="
git diff

# Step 6: Validation
echo ""
echo "========================================="
echo "Validation Results:"
echo "========================================="

# Check POM changes
echo ""
echo "Checking pom.xml changes..."
if ! grep -q "camel-stomp" pom.xml; then
    echo "✅ camel-stomp removed"
else
    echo "❌ camel-stomp NOT removed"
fi

if ! grep -q "camel-aws-xray" pom.xml; then
    echo "✅ camel-aws-xray removed"
else
    echo "❌ camel-aws-xray NOT removed"
fi

if grep -q "io.github.whatap" pom.xml; then
    echo "✅ java-grok groupId changed to io.github.whatap"
else
    echo "❌ java-grok groupId NOT changed"
fi

if grep -q "commons-dbcp2" pom.xml; then
    echo "✅ commons-dbcp changed to commons-dbcp2"
else
    echo "❌ commons-dbcp NOT changed"
fi

# Check Java code changes
echo ""
echo "Checking Java code changes..."
if grep -q "CamelKafkaTopic" src/main/java/com/test/MyKafkaRoute.java; then
    echo "✅ kafka.TOPIC renamed to CamelKafkaTopic"
else
    echo "❌ kafka.TOPIC NOT renamed"
fi

if grep -q "CamelKafkaKey" src/main/java/com/test/MyKafkaRoute.java; then
    echo "✅ kafka.KEY renamed to CamelKafkaKey"
else
    echo "❌ kafka.KEY NOT renamed"
fi

if grep -q "CamelJGroupsDest" src/main/java/com/test/MyJGroupsRoute.java; then
    echo "✅ JGROUPS_DEST renamed to CamelJGroupsDest"
else
    echo "❌ JGROUPS_DEST NOT renamed"
fi

# Final summary
echo ""
echo "========================================="
echo "Test complete!"
echo "Test directory: $TEST_DIR"
echo "You can inspect the changes manually:"
echo "  cd $TEST_DIR"
echo "  git diff"
echo "========================================="
