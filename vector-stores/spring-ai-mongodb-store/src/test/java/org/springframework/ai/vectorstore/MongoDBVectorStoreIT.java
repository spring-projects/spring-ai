/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Chris Smith
 */
@Testcontainers
class MongoDBVectorStoreIT {


    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("SPRING_AI_OPENAI_API_KEY"),

                    // JdbcTemplate configuration
                    String.format("spring.data.mongodb.uri="+System.getenv("SPRING_DATA_MONGODB_URI"))
                    );


    @BeforeEach
    public void afterEverything(){
        contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

            MongoTemplate mongoTemplate = context.getBean(MongoTemplate.class);
            mongoTemplate.getCollection("vector_store").deleteMany(new org.bson.Document());
        });
    }
    @Test
    void vectorStoreTest() {
        contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

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
            Thread.sleep(5000);            //Await a second for the document to be indexed

            List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
            assertThat(resultDoc.getContent()).isEqualTo(
                    "Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
            assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

            // Remove all documents from the store
            vectorStore.delete(documents.stream().map(doc -> doc.getId()).collect(Collectors.toList()));

            List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));
            assertThat(results2).isEmpty();

        });
    }

    @Test
    void documentUpdateTest() {
        contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

            VectorStore vectorStore = context.getBean(VectorStore.class);

            Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
                    Collections.singletonMap("meta1", "meta1"));

            vectorStore.add(List.of(document));
            Thread.sleep(5000);            //Await a second for the document to be indexed


            List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(document.getId());
            assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
            assertThat(resultDoc.getMetadata()).containsEntry("meta1", "meta1");

            Document sameIdDocument = new Document(document.getId(),
                    "The World is Big and Salvation Lurks Around the Corner",
                    Collections.singletonMap("meta2", "meta2"));

            vectorStore.add(List.of(sameIdDocument));

            results = vectorStore.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

            assertThat(results).hasSize(1);
            resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(document.getId());
            assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
            assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        public VectorStore vectorStore(MongoTemplate mongoTemplate, EmbeddingClient embeddingClient) {
            return new MongoDBVectorStore(mongoTemplate, embeddingClient);
        }

        @Bean
        public MongoTemplate mongoTemplate(MongoClient mongoClient) {
            return new MongoTemplate(mongoClient, "springaisample");
        }

        @Bean
        public EmbeddingClient embeddingClient() {
            return new OpenAiEmbeddingClient(new OpenAiApi(System.getenv("SPRING_AI_OPENAI_API_KEY")));
        }
    }

}