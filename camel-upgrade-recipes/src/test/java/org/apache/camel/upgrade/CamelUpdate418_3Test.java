/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.upgrade;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * Tests for migrating from Camel 4.18.1 to 4.18.3.
 * Most changes are header renames that were introduced in 4.18.x and reused from 4.21 recipes.
 */
public class CamelUpdate418_3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        CamelTestUtil.recipe(spec, CamelTestUtil.CamelVersion.v4_18_3)
                .parser(CamelTestUtil.parserFromClasspath(CamelTestUtil.CamelVersion.v4_18, "camel-api",
                        "camel-core-model", "camel-support"))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void testLuceneHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-lucene",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-lucene", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("QUERY", "lucene query");
                                        exchange.getIn().setHeader("RETURN_LUCENE_DOCS", true);
                                    })
                                    .to("lucene:insert");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelLuceneQuery", "lucene query");
                                        exchange.getIn().setHeader("CamelLuceneReturnLuceneDocs", true);
                                    })
                                    .to("lucene:insert");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testPdfHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-pdf",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-pdf", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("pdf-document", "document");
                                        exchange.getIn().setHeader("protection-policy", "policy");
                                        exchange.getIn().setHeader("decryption-material", "material");
                                        exchange.getIn().setHeader("files-to-merge", "files");
                                    })
                                    .to("pdf:create");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelPdfDocument", "document");
                                        exchange.getIn().setHeader("CamelPdfProtectionPolicy", "policy");
                                        exchange.getIn().setHeader("CamelPdfDecryptionMaterial", "material");
                                        exchange.getIn().setHeader("CamelPdfFilesToMerge", "files");
                                    })
                                    .to("pdf:create");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testArangoDbHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-arangodb",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-arangodb", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("key", "myKey");
                                        exchange.getIn().setHeader("ResultClassType", String.class);
                                    })
                                    .to("arangodb:myDb");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelArangoDbKey", "myKey");
                                        exchange.getIn().setHeader("CamelArangoDbResultClassType", String.class);
                                    })
                                    .to("arangodb:myDb");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testJt400HeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-jt400",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-jt400", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("KEY", "myKey");
                                        exchange.getIn().setHeader("SENDER_INFORMATION", "sender");
                                    })
                                    .to("jt400:program");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelJt400Key", "myKey");
                                        exchange.getIn().setHeader("CamelJt400SenderInformation", "sender");
                                    })
                                    .to("jt400:program");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testMailHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-mail",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-mail", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("imap://server")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("copyTo", "Archive");
                                        exchange.getIn().setHeader("moveTo", "Processed");
                                        exchange.getIn().setHeader("delete", true);
                                    })
                                    .to("direct:process");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("imap://server")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelMailCopyTo", "Archive");
                                        exchange.getIn().setHeader("CamelMailMoveTo", "Processed");
                                        exchange.getIn().setHeader("CamelMailDelete", true);
                                    })
                                    .to("direct:process");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testMiloHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-milo",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-milo", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("await", true);
                                    })
                                    .to("milo-client:opc.tcp://localhost");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelMiloAwait", true);
                                    })
                                    .to("milo-client:opc.tcp://localhost");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testElasticsearchHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-elasticsearch",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-elasticsearch", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("operation", "INDEX");
                                        exchange.getIn().setHeader("indexId", "123");
                                        exchange.getIn().setHeader("indexName", "my-index");
                                        exchange.getIn().setHeader("documentClass", String.class);
                                    })
                                    .to("elasticsearch:myCluster");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelElasticsearchOperation", "INDEX");
                                        exchange.getIn().setHeader("CamelElasticsearchIndexId", "123");
                                        exchange.getIn().setHeader("CamelElasticsearchIndexName", "my-index");
                                        exchange.getIn().setHeader("CamelElasticsearchDocumentClass", String.class);
                                    })
                                    .to("elasticsearch:myCluster");
                            }
                        }
                        """
                )
                )
        );
    }
    @Test
    void testOpensearchHeadersMigrationJava() {
        //language=java
        rewriteRun(
                mavenProject("test-opensearch",
                        pomXml(CamelTestUtil.pomXmlWithDependency("camel-opensearch", CamelTestUtil.CamelVersion.v4_18)),
                        java(
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("operation", "INDEX");
                                        exchange.getIn().setHeader("indexId", "123");
                                        exchange.getIn().setHeader("indexName", "my-index");
                                        exchange.getIn().setHeader("documentClass", String.class);
                                    })
                                    .to("opensearch:myCluster");
                            }
                        }
                        """,
                        """
                        import org.apache.camel.Exchange;
                        import org.apache.camel.builder.RouteBuilder;

                        class Test extends RouteBuilder {
                            public void configure() {
                                from("direct:start")
                                    .process(exchange -> {
                                        exchange.getIn().setHeader("CamelOpensearchOperation", "INDEX");
                                        exchange.getIn().setHeader("CamelOpensearchIndexId", "123");
                                        exchange.getIn().setHeader("CamelOpensearchIndexName", "my-index");
                                        exchange.getIn().setHeader("CamelOpensearchDocumentClass", String.class);
                                    })
                                    .to("opensearch:myCluster");
                            }
                        }
                        """
                )
                )
        );
    }

}
