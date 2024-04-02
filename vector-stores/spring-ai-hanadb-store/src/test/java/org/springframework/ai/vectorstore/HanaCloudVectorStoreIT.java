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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@Testcontainers
public class HanaCloudVectorStoreIT {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(HanaTestApplication.class);

    @Test
    public void vectorStoreTest() {
        contextRunner
                .run(context -> {

                    VectorStore hanaCloudVectorStore = context.getBean(VectorStore.class);
                    Supplier<List<Document>> reader = new PagePdfDocumentReader("classpath:Cricket_World_Cup.pdf");
                    Function<List<Document>, List<Document>> splitter = new TokenTextSplitter();
                    List<Document> documents = splitter.apply(reader.get());
                    hanaCloudVectorStore.accept(documents);

                    List<Document> results = hanaCloudVectorStore.similaritySearch("Who won the 2023 cricket world cup finals?");
                    Assertions.assertEquals(1, results.size());
                    Assertions.assertTrue(results.get(0).getContent().contains("Australia"));

                    // Remove all documents from the store
                    hanaCloudVectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));
                    List<Document> results2 = hanaCloudVectorStore.similaritySearch("Australia");
                    Assertions.assertEquals(0, results2.size());
                });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class HanaTestApplication {
        @Bean
        public VectorStore hanaCloudVectorStore(CricketWorldCupRepository cricketWorldCupRepository,
                                                EmbeddingClient embeddingClient) {
            return new HanaCloudVectorStore(cricketWorldCupRepository, embeddingClient,
                    HanaCloudVectorStoreConfig.builder()
                            .tableName("CRICKET_WORLD_CUP")
                            .topK(1)
                            .build());
        }
    }
}