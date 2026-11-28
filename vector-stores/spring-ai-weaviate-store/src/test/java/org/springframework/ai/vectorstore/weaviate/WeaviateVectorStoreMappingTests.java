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

package org.springframework.ai.vectorstore.weaviate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.data.DeleteManyResponse;
import io.weaviate.client6.v1.api.collections.data.DeleteManyResponse.DeletedObject;
import io.weaviate.client6.v1.api.collections.data.InsertManyResponse;
import io.weaviate.client6.v1.api.collections.data.InsertManyResponse.InsertObject;
import io.weaviate.client6.v1.api.collections.query.QueryMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Focused unit tests for {@link WeaviateVectorStore} result mapping and partial-failure
 * aggregation, exercised without a live Weaviate server.
 *
 * @author Soby Chacko
 */
@ExtendWith(MockitoExtension.class)
class WeaviateVectorStoreMappingTests {

	@Mock
	private EmbeddingModel embeddingModel;

	@Mock
	private WeaviateClient weaviateClient;

	private WeaviateVectorStore store() {
		return WeaviateVectorStore.builder(this.weaviateClient, this.embeddingModel).build();
	}

	private static WeaviateObject<Map<String, Object>> object(String uuid, Map<String, Object> properties,
			QueryMetadata queryMetadata) {
		return new WeaviateObject<>(uuid, null, null, properties, null, null, null, queryMetadata, null);
	}

	@Test
	void mapsUuidContentMetadataAndCertainty() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("content", "hello world");
		properties.put("metadata", "{\"country\":\"BG\",\"year\":2020}");

		Document document = store().toDocument(object("doc-1", properties, new QueryMetadata(null, 0.95f, null, null)));

		assertThat(document.getId()).isEqualTo("doc-1");
		assertThat(document.getText()).isEqualTo("hello world");
		assertThat(document.getScore()).isCloseTo(0.95, within(1e-6));
		assertThat(document.getMetadata()).containsEntry("country", "BG");
		assertThat(document.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
		assertThat((Double) document.getMetadata().get(DocumentMetadata.DISTANCE.value())).isCloseTo(0.05,
				within(1e-6));
	}

	@Test
	void toleratesMissingMetadataJson() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("content", "only content, no metadata property");

		Document document = store().toDocument(object("doc-2", properties, new QueryMetadata(null, 1.0f, null, null)));

		assertThat(document.getId()).isEqualTo("doc-2");
		assertThat(document.getText()).isEqualTo("only content, no metadata property");
		assertThat(document.getScore()).isCloseTo(1.0, within(1e-6));
		// Only the computed distance is present; no document metadata was stored.
		assertThat(document.getMetadata()).containsOnlyKeys(DocumentMetadata.DISTANCE.value());
	}

	@Test
	void throwsWhenContentIsMissing() {
		// A result without the content property cannot build a Document (which requires
		// text or media); this underpins the custom-content-field integration test.
		assertThatThrownBy(() -> store()
			.toDocument(object("doc-missing", new HashMap<>(), new QueryMetadata(null, 1.0f, null, null))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly one of text or media must be specified");
	}

	@Test
	void throwsWhenCertaintyIsMissing() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("content", "hello");

		// A null certainty indicates a non-cosine collection.
		assertThatThrownBy(
				() -> store().toDocument(object("doc-3", properties, new QueryMetadata(0.3f, null, null, null))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cosine");
	}

	@Test
	void throwsWhenUuidIsMissing() {
		assertThatThrownBy(
				() -> store().toDocument(object(null, new HashMap<>(), new QueryMetadata(null, 0.9f, null, null))))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void collectsInsertErrorsFromErrorsAndResponses() {
		InsertManyResponse response = new InsertManyResponse(1.0f,
				List.of(new InsertObject("ok", true, null), new InsertObject("bad", false, "object failed")),
				List.of("ok"), List.of("top-level error"));

		assertThat(WeaviateVectorStore.collectInsertErrors(response)).containsExactly("top-level error",
				"object failed");
	}

	@Test
	void collectsNoInsertErrorsWhenAllSuccessful() {
		InsertManyResponse response = new InsertManyResponse(1.0f, List.of(new InsertObject("ok", true, null)),
				List.of("ok"), List.of());

		assertThat(WeaviateVectorStore.collectInsertErrors(response)).isEmpty();
	}

	@Test
	void deleteThrowsOnFailedCount() {
		DeleteManyResponse response = new DeleteManyResponse(1.0f, 1, 1, 0, List.of());

		assertThatThrownBy(() -> WeaviateVectorStore.throwOnDeleteFailure(response))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Failed to delete documents");
	}

	@Test
	void deleteThrowsOnUnsuccessfulObject() {
		DeleteManyResponse response = new DeleteManyResponse(1.0f, 0, 1, 0,
				List.of(new DeletedObject("bad", false, "could not delete")));

		assertThatThrownBy(() -> WeaviateVectorStore.throwOnDeleteFailure(response))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("could not delete");
	}

	@Test
	void deleteDoesNotThrowWhenSuccessful() {
		DeleteManyResponse response = new DeleteManyResponse(1.0f, 0, 2, 2,
				List.of(new DeletedObject("a", true, null), new DeletedObject("b", true, null)));

		WeaviateVectorStore.throwOnDeleteFailure(response);
	}

}
