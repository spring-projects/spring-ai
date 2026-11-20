/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import java.util.List;

import com.azure.search.documents.indexes.models.SearchFieldDataType;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.azure.AzureVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Aditya Vijay
 */
public class AzureVectorStoreAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AzureVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	public void metadataFieldsAreBoundFromProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.azure.metadata-fields[0].name=filterField",
					"spring.ai.vectorstore.azure.metadata-fields[0].field-type=string",
					"spring.ai.vectorstore.azure.metadata-fields[1].name=count",
					"spring.ai.vectorstore.azure.metadata-fields[1].field-type=int32",
					"spring.ai.vectorstore.azure.metadata-fields[2].name=price",
					"spring.ai.vectorstore.azure.metadata-fields[2].field-type=Edm.Double")
			.run(context -> {
				var properties = context.getBean(AzureVectorStoreProperties.class);

				assertThat(properties.getMetadataFields()).containsExactly(
						new AzureVectorStore.MetadataField("filterField", SearchFieldDataType.STRING),
						new AzureVectorStore.MetadataField("count", SearchFieldDataType.INT32),
						new AzureVectorStore.MetadataField("price", SearchFieldDataType.DOUBLE));
			});
	}

	@Test
	public void metadataFieldsDefaultToEmpty() {
		this.contextRunner.run(context -> {
			var properties = context.getBean(AzureVectorStoreProperties.class);

			assertThat(properties.getMetadataFields()).isEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(AzureVectorStore.class);
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void vectorStoreReceivesMetadataFieldsFromProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.azure.metadata-fields[0].name=filterField",
					"spring.ai.vectorstore.azure.metadata-fields[0].field-type=string")
			.run(context -> {
				AzureVectorStore vectorStore = context.getBean(AzureVectorStore.class);

				var filterMetadataFields = (List<AzureVectorStore.MetadataField>) ReflectionTestUtils
					.getField(vectorStore, "filterMetadataFields");
				assertThat(filterMetadataFields)
					.containsExactly(new AzureVectorStore.MetadataField("filterField", SearchFieldDataType.STRING));
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(1536);
		}

	}

}
