/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.autoconfigure.vectorstore.observation.ObservationTestUtil.assertObservationRegistry;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

/**
 * @author Eddú Meléndez
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ignacio López
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class MongoDBAtlasVectorStoreAutoConfigurationIT {

	@Container
	static MongoDBAtlasLocalContainer mongo = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:7.0.9");

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")),
			new Document(
					"Testcontainers Testcontainers Testcontainers Testcontainers Testcontainers Testcontainers Testcontainers",
					Collections.singletonMap("foo", "bar")),
			new Document(
					"Testcontainers Testcontainers Testcontainers Testcontainers Testcontainers Testcontainers Testcontainers",
					Collections.singletonMap("foo", "baz")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class)
		.withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class, MongoDataAutoConfiguration.class,
				MongoDBAtlasVectorStoreAutoConfiguration.class, RestClientAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class, OpenAiAutoConfiguration.class))
		.withPropertyValues("spring.data.mongodb.database=springaisample",
				"spring.ai.vectorstore.mongodb.initialize-schema=true",
				"spring.ai.vectorstore.mongodb.collection-name=test_collection",
				// "spring.ai.vectorstore.mongodb.path-name=testembedding",
				"spring.ai.vectorstore.mongodb.index-name=text_index",
				"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				String.format("spring.data.mongodb.uri=" + mongo.getConnectionString()));

	@Test
	public void addAndSearch() {
		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(documents);
			assertObservationRegistry(observationRegistry, VectorStoreProvider.MONGODB,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			Thread.sleep(5000); // Await a second for the document to be indexed

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

			assertObservationRegistry(observationRegistry, VectorStoreProvider.MONGODB,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));

			assertObservationRegistry(observationRegistry, VectorStoreProvider.MONGODB,
					VectorStoreObservationContext.Operation.DELETE);
			observationRegistry.clear();

			List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));
			assertThat(results2).isEmpty();

			context.getBean(MongoTemplate.class).dropCollection("test_collection");
		});
	}

	@Test
	public void addAndSearchWithFilters() {
		contextRunner.withPropertyValues("spring.ai.vectorstore.mongodb.metadata-fields-to-filter=foo").run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(documents);

			Thread.sleep(5000); // Await a second for the document to be indexed

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Testcontainers").withTopK(2));
			assertThat(results).hasSize(2);
			results.forEach(doc -> assertThat(doc.getContent().contains("Testcontainers")).isTrue());

			FilterExpressionBuilder b = new FilterExpressionBuilder();
			results = vectorStore.similaritySearch(
					SearchRequest.query("Testcontainers").withTopK(2).withFilterExpression(b.eq("foo", "bar").build()));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(3).getId());
			assertThat(resultDoc.getContent().contains("Testcontainers")).isTrue();
			assertThat(resultDoc.getMetadata()).containsEntry("foo", "bar");

			context.getBean(MongoTemplate.class).dropCollection("test_collection");
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

	}

}