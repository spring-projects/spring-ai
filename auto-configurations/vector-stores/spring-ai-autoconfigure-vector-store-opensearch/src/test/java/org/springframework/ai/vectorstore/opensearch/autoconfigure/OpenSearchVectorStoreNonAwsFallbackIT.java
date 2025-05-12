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

package org.springframework.ai.vectorstore.opensearch.autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OpenSearchVectorStoreNonAwsFallbackIT {

	@Container
	private static final OpensearchContainer<?> opensearchContainer = new OpensearchContainer<>(
			DockerImageName.parse("opensearchproject/opensearch:2.13.0"));

	private static final String DOCUMENT_INDEX = "nonaws-spring-ai-document-index";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenSearchVectorStoreAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.opensearch.aws.enabled=false",
				"spring.ai.vectorstore.opensearch.uris=" + opensearchContainer.getHttpHostAddress(),
				"spring.ai.vectorstore.opensearch.indexName=" + DOCUMENT_INDEX,
				"spring.ai.vectorstore.opensearch.mappingJson={\"properties\":{\"embedding\":{\"type\":\"knn_vector\",\"dimension\":384}}}");

	private List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@Test
	void nonAwsFallbackConfigurationWorks() {
		this.contextRunner.run(context -> {
			// AWS-specific bean should NOT be present
			assertThat(context.containsBeanDefinition("awsOpenSearchConnectionDetails")).isFalse();
			// Standard OpenSearch bean should be present
			assertThat(context.getBeansOfType(OpenSearchConnectionDetails.class)).isNotEmpty();
			// OpenSearchVectorStore should still be present
			assertThat(context.getBeansOfType(OpenSearchVectorStore.class)).isNotEmpty();
		});
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
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
