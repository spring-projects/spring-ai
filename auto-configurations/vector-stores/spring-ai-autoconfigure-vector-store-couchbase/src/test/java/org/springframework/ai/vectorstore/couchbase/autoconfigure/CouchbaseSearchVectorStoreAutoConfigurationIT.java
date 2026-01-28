/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.couchbase.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.couchbase.CouchbaseIndexOptimization;
import org.springframework.ai.vectorstore.couchbase.CouchbaseSimilarityFunction;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.couchbase.autoconfigure.CouchbaseAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class CouchbaseSearchVectorStoreAutoConfigurationIT {

	// Define the couchbase container.
	@Container
	final static CouchbaseContainer couchbaseContainer = new CouchbaseContainer(
			CouchbaseContainerMetadata.COUCHBASE_IMAGE_ENTERPRISE)
		.withCredentials(CouchbaseContainerMetadata.USERNAME, CouchbaseContainerMetadata.PASSWORD)
		.withEnabledServices(CouchbaseService.KV, CouchbaseService.QUERY, CouchbaseService.INDEX,
				CouchbaseService.SEARCH)
		.withBucket(CouchbaseContainerMetadata.bucketDefinition)
		.withStartupAttempts(4)
		.withStartupTimeout(Duration.ofSeconds(90))
		.waitingFor(Wait.forHealthcheck());

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CouchbaseAutoConfiguration.class,
				CouchbaseSearchVectorStoreAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class))
		.withPropertyValues("spring.couchbase.connection-string=" + couchbaseContainer.getConnectionString(),
				"spring.couchbase.username=" + couchbaseContainer.getUsername(),
				"spring.couchbase.password=" + couchbaseContainer.getPassword(),
				"spring.ai.vectorstore.couchbase.initialize-schema=true",
				"spring.ai.vectorstore.couchbase.index-name=example",
				"spring.ai.vectorstore.couchbase.collection-name=example",
				"spring.ai.vectorstore.couchbase.scope-name=example",
				"spring.ai.vectorstore.couchbase.bucket-name=example",
				"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"));

	@Test
	public void addAndSearchWithFilters() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands"));

			vectorStore.add(List.of(bgDocument, nlDocument));

			var requestBuilder = SearchRequest.builder().query("The World").topK(5);

			List<Document> results = vectorStore.similaritySearch(requestBuilder.build());
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch(
					requestBuilder.similarityThresholdAll().filterExpression("country == 'Bulgaria'").build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(
					requestBuilder.similarityThresholdAll().filterExpression("country == 'Netherlands'").build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());
		});
	}

	@Test
	public void propertiesTest() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(CouchbaseAutoConfiguration.class,
					CouchbaseSearchVectorStoreAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.couchbase.connection-string=" + couchbaseContainer.getConnectionString(),
					"spring.couchbase.username=" + couchbaseContainer.getUsername(),
					"spring.couchbase.password=" + couchbaseContainer.getPassword(),
					"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
					"spring.ai.vectorstore.couchbase.index-name=example",
					"spring.ai.vectorstore.couchbase.collection-name=example",
					"spring.ai.vectorstore.couchbase.scope-name=example",
					"spring.ai.vectorstore.couchbase.bucket-name=example",
					"spring.ai.vectorstore.couchbase.dimensions=1024",
					"spring.ai.vectorstore.couchbase.optimization=latency",
					"spring.ai.vectorstore.couchbase.similarity=l2_norm")
			.run(context -> {
				var properties = context.getBean(CouchbaseSearchVectorStoreProperties.class);
				var vectorStore = context.getBean(VectorStore.class);

				assertThat(properties).isNotNull();
				assertThat(properties.getIndexName()).isEqualTo("example");
				assertThat(properties.getCollectionName()).isEqualTo("example");
				assertThat(properties.getScopeName()).isEqualTo("example");
				assertThat(properties.getBucketName()).isEqualTo("example");
				assertThat(properties.getDimensions()).isEqualTo(1024);
				assertThat(properties.getOptimization()).isEqualTo(CouchbaseIndexOptimization.latency);
				assertThat(properties.getSimilarity()).isEqualTo(CouchbaseSimilarityFunction.l2_norm);

				assertThat(vectorStore).isNotNull();
			});
	}

}
