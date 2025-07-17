/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.weaviate;

import java.util.List;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.ConsistentLevel;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.MetadataField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WeaviateVectorStore.Builder}.
 *
 * @author Mark Pollack
 * @author Jonghoon Park
 */
@ExtendWith(MockitoExtension.class)
class WeaviateVectorStoreBuilderTests {

	@Mock
	private EmbeddingModel embeddingModel;

	@Test
	void shouldBuildWithMinimalConfiguration() {
		WeaviateClient weaviateClient = new WeaviateClient(new Config("http", "localhost:8080"));

		WeaviateVectorStore vectorStore = WeaviateVectorStore.builder(weaviateClient, this.embeddingModel).build();

		assertThat(vectorStore).isNotNull();
	}

	@Test
	void shouldBuildWithCustomConfiguration() {
		WeaviateClient weaviateClient = new WeaviateClient(new Config("http", "localhost:8080"));

		WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();
		options.setObjectClass("CustomObjectClass");
		options.setContentFieldName("customContentFieldName");
		options.setMetaFieldPrefix("custom_");

		WeaviateVectorStore vectorStore = WeaviateVectorStore.builder(weaviateClient, this.embeddingModel)
			.options(options)
			.consistencyLevel(ConsistentLevel.QUORUM)
			.filterMetadataFields(List.of(MetadataField.text("country"), MetadataField.number("year")))
			.build();

		assertThat(vectorStore).isNotNull();
	}

	@Test
	void shouldFailWithoutWeaviateClient() {
		assertThatThrownBy(() -> WeaviateVectorStore.builder(null, this.embeddingModel).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("WeaviateClient must not be null");
	}

	@Test
	void shouldFailWithoutEmbeddingModel() {
		WeaviateClient weaviateClient = new WeaviateClient(new Config("http", "localhost:8080"));

		assertThatThrownBy(() -> WeaviateVectorStore.builder(weaviateClient, null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("EmbeddingModel must be configured");
	}

	@Test
	void shouldFailWithNullOptions() {
		WeaviateClient weaviateClient = new WeaviateClient(new Config("http", "localhost:8080"));

		assertThatThrownBy(() -> WeaviateVectorStore.builder(weaviateClient, this.embeddingModel).options(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("options must not be empty");
	}

	@Test
	void shouldFailWithNullConsistencyLevel() {
		WeaviateClient weaviateClient = new WeaviateClient(new Config("http", "localhost:8080"));

		assertThatThrownBy(
				() -> WeaviateVectorStore.builder(weaviateClient, this.embeddingModel).consistencyLevel(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("consistencyLevel must not be null");
	}

	@Test
	void shouldFailWithNullFilterMetadataFields() {
		WeaviateClient weaviateClient = new WeaviateClient(new Config("http", "localhost:8080"));

		assertThatThrownBy(() -> WeaviateVectorStore.builder(weaviateClient, this.embeddingModel)
			.filterMetadataFields(null)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("filterMetadataFields must not be null");
	}

	@Test
	void shouldCreateMetadataFieldsWithValidation() {
		assertThatThrownBy(() -> MetadataField.text("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Text field must not be empty");

		assertThatThrownBy(() -> MetadataField.number("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Number field must not be empty");

		assertThatThrownBy(() -> MetadataField.bool("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Boolean field name must not be empty");

		MetadataField textField = MetadataField.text("validName");
		assertThat(textField.name()).isEqualTo("validName");
		assertThat(textField.type()).isEqualTo(MetadataField.Type.TEXT);

		MetadataField numberField = MetadataField.number("validName");
		assertThat(numberField.name()).isEqualTo("validName");
		assertThat(numberField.type()).isEqualTo(MetadataField.Type.NUMBER);

		MetadataField boolField = MetadataField.bool("validName");
		assertThat(boolField.name()).isEqualTo("validName");
		assertThat(boolField.type()).isEqualTo(MetadataField.Type.BOOLEAN);
	}

}
