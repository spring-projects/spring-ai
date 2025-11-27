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

package org.springframework.ai.vectorstore.infinispan.autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.awaitility.Awaitility;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.commons.util.Version;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.spring.starter.remote.InfinispanRemoteAutoConfiguration;
import org.infinispan.testcontainers.InfinispanContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.infinispan.InfinispanVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Katia Aresti
 */
@Testcontainers
class InfinispanVectorStoreAutoConfigurationIT {

	@Container
	private static final InfinispanContainer infinispanContainer = new InfinispanContainer(
			InfinispanContainer.IMAGE_BASENAME + ":" + Version.getVersion());

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(InfinispanRemoteAutoConfiguration.class,
				InfinispanVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.infinispan.distance=" + 10,
				"infinispan.remote.server-list=" + serverList(),
				"infinispan.remote.auth-username=" + InfinispanContainer.DEFAULT_USERNAME,
				// Needs the marshalling property until fix
				// https://github.com/infinispan/infinispan/issues/16440
				"infinispan.remote.marshaller=" + ProtoStreamMarshaller.class.getName(),
				"infinispan.remote.auth-password=" + InfinispanContainer.DEFAULT_PASSWORD);

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Test
	public void addAndSearchTest() {
		this.contextRunner.run(context -> {
			InfinispanVectorStore vectorStore = context.getBean(InfinispanVectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(this.documents);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.INFINISPAN,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(1));

			observationRegistry.clear();

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(1);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.INFINISPAN,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.INFINISPAN,
					VectorStoreObservationContext.Operation.DELETE);
			observationRegistry.clear();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(0));
		});
	}

	@Test
	public void propertiesTest() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(InfinispanVectorStoreAutoConfiguration.class,
					InfinispanRemoteAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.withPropertyValues("infinispan.remote.server-list=" + serverList(),
					"infinispan.remote.auth-username=" + InfinispanContainer.DEFAULT_USERNAME,
					"infinispan.remote.auth-password=" + InfinispanContainer.DEFAULT_PASSWORD,
					"spring.ai.vectorstore.infinispan.distance=20",
					"spring.ai.vectorstore.infinispan.item-name=ItemExample",
					"spring.ai.vectorstore.infinispan.metadata-item-name=MetadataExample",
					"spring.ai.vectorstore.infinispan.package-name=exam.pac",
					"spring.ai.vectorstore.infinispan.store-name=mycoolstore",
					"spring.ai.vectorstore.infinispan.schema-file-name=schemaName.proto",
					"spring.ai.vectorstore.infinispan.similarity=cosine")
			.run(context -> {
				var properties = context.getBean(InfinispanVectorStoreProperties.class);
				assertThat(properties).isNotNull();
				assertThat(properties.getDistance()).isEqualTo(20);
				assertThat(properties.getItemName()).isEqualTo("ItemExample");
				assertThat(properties.getMetadataItemName()).isEqualTo("MetadataExample");
				assertThat(properties.getSimilarity()).isEqualTo("cosine");

				InfinispanVectorStore infinispanVectorStore = context.getBean(InfinispanVectorStore.class);
				assertThat(infinispanVectorStore).isNotNull();

				RemoteCacheManager cacheManager = context.getBean(RemoteCacheManager.class);
				RemoteCache cache = cacheManager.getCache("mycoolstore");
				assertThat(cache).isNotNull();
				Optional<Schema> schema = cacheManager.administration().schemas().get("schemaName.proto");
				assertThat(schema).isNotEmpty();
				String schemaContent = schema.get().getContent();
				assertThat(schemaContent).contains("ItemExample");
				assertThat(schemaContent).contains("MetadataExample");
				assertThat(schemaContent).contains("package exam.pac");
			});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(InfinispanVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(InfinispanVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(InfinispanVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(InfinispanVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsInfinispan() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=infinispan").run(context -> {
			assertThat(context.getBeansOfType(InfinispanVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(InfinispanVectorStore.class);
		});
	}

	private static @NotNull String serverList() {
		return infinispanContainer.getHost() + ":"
				+ infinispanContainer.getMappedPort(InfinispanContainer.DEFAULT_HOTROD_PORT);
	}

	private String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
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
