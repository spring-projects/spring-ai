/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.cassandra.autoconfigure;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Testcontainers
class CassandraVectorStoreAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("cassandra");

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(DEFAULT_IMAGE_NAME.withTag("5.0"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(CassandraVectorStoreAutoConfiguration.class, CassandraAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.cassandra.initialize-schema=true")
		.withPropertyValues("spring.ai.vectorstore.cassandra.keyspace=test_autoconfigure")
		.withPropertyValues("spring.ai.vectorstore.cassandra.contentColumnName=doc_chunk");

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Test
	void addAndSearch() {
		this.contextRunner.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + cassandraContainer.getLocalDatacenter())
			.withPropertyValues("spring.ai.vectorstore.cassandra.fixedThreadPoolExecutorSize=8")

			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);
				vectorStore.add(this.documents);

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.CASSANDRA,
						VectorStoreObservationContext.Operation.ADD);
				observationRegistry.clear();

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.CASSANDRA,
						VectorStoreObservationContext.Operation.QUERY);
				observationRegistry.clear();

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
				assertThat(results).isEmpty();

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.CASSANDRA,
						VectorStoreObservationContext.Operation.DELETE);
				observationRegistry.clear();
			});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(CassandraVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(CassandraVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + cassandraContainer.getLocalDatacenter())
			.withPropertyValues("spring.ai.vectorstore.cassandra.fixedThreadPoolExecutorSize=8")
			.run(context -> {
				assertThat(context.getBeansOfType(CassandraVectorStoreProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
				assertThat(context.getBean(VectorStore.class)).isInstanceOf(CassandraVectorStore.class);
			});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsCassandra() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=cassandra")
			.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + cassandraContainer.getLocalDatacenter())
			.withPropertyValues("spring.ai.vectorstore.cassandra.fixedThreadPoolExecutorSize=8")
			.run(context -> {
				assertThat(context.getBeansOfType(CassandraVectorStoreProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
				assertThat(context.getBean(VectorStore.class)).isInstanceOf(CassandraVectorStore.class);
			});
	}

	private String getContactPointHost() {
		return cassandraContainer.getContactPoint().getHostString();
	}

	private String getContactPointPort() {
		return String.valueOf(cassandraContainer.getContactPoint().getPort());
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
