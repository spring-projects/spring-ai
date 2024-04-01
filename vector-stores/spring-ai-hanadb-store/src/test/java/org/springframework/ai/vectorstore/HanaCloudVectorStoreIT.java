/*
 * Copyright 2024 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@Testcontainers
public class HanaCloudVectorStoreIT {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(HanaTestApplication.class);
//            .withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("SPRING_AI_OPENAI_API_KEY"),

                    // JdbcTemplate configuration
//                    String.format("spring.data.mongodb.uri=mongodb://localhost:%d/test"));


    @Test
    public void vectorStoreTest() {
        contextRunner
//                .withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
                .run(context -> {

            VectorStore vectorStore = context.getBean(VectorStore.class);

            List<Document> documents = List.of(
                    new Document(
                            "Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
                            Collections.singletonMap("meta1", "meta1")),
                    new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
                    new Document(
                            "Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
                            Collections.singletonMap("meta2", "meta2")));

            vectorStore.add(documents);

            List<Document> results = vectorStore.similaritySearch("Great");

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
            assertThat(resultDoc.getContent()).isEqualTo(
                    "Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
            assertThat(resultDoc.getMetadata().get("meta2")).isEqualTo("meta2");

            // Remove all documents from the store
            vectorStore.delete(documents.stream().map(doc -> doc.getId()).collect(Collectors.toList()));

            List<Document> results2 = vectorStore.similaritySearch("Great");
            assertThat(results2).hasSize(0);

        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class HanaTestApplication {

        @Value("${hana.table.name}")
        private String tableName = "CRICKET_WORLD_CUP";

        @Value("${hana.similarity.search.topK}")
        private int topK;

        @Bean
        public VectorStore hanaCloudVectorStore(CricketWorldCupRepository cricketWorldCupRepository,
                                                EmbeddingClient embeddingClient) {
            return new HanaCloudVectorStore(cricketWorldCupRepository, embeddingClient,
                    HanaCloudVectorStoreConfig.builder()
                            .tableName(tableName)
                            .topK(topK)
                            .build());
        }

    }

}