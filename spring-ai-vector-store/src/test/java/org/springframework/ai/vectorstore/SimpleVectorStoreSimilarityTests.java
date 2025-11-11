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

package org.springframework.ai.vectorstore;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 */
public class SimpleVectorStoreSimilarityTests {

	@Test
	public void testSimilarity() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("foo", "bar");
		float[] testEmbedding = new float[] { 1.0f, 2.0f, 3.0f };

		SimpleVectorStoreContent storeContent = new SimpleVectorStoreContent("1", "hello, how are you?", metadata,
				testEmbedding);
		Document document = storeContent.toDocument(0.6);
		assertThat(document).isNotNull();
		assertThat(document.getId()).isEqualTo("1");
		assertThat(document.getText()).isEqualTo("hello, how are you?");
		assertThat(document.getMetadata().get("foo")).isEqualTo("bar");
	}

	@Test
	public void testEmptyId() {
		Map<String, Object> metadata = new HashMap<>();
		float[] embedding = new float[] { 1.0f };

		assertThatThrownBy(() -> new SimpleVectorStoreContent("", "text content", metadata, embedding))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id must not be null or empty");
	}

	@Test
	public void testEmptyEmbeddingArray() {
		Map<String, Object> metadata = new HashMap<>();
		float[] emptyEmbedding = new float[0];

		assertThatThrownBy(() -> new SimpleVectorStoreContent("valid-id", "text content", metadata, emptyEmbedding))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embedding vector must not be empty");
	}

	@Test
	public void testSingleElementEmbedding() {
		Map<String, Object> metadata = new HashMap<>();
		float[] singleEmbedding = new float[] { 0.1f };

		SimpleVectorStoreContent storeContent = new SimpleVectorStoreContent("id-1", "text", metadata, singleEmbedding);
		Document document = storeContent.toDocument(0.1);

		assertThat(document).isNotNull();
		assertThat(document.getScore()).isEqualTo(0.1);
	}

	@Test
	public void testNullMetadata() {
		float[] embedding = new float[] { 1.0f };

		assertThatThrownBy(() -> new SimpleVectorStoreContent("id-1", "text", null, embedding))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata must not be null");
	}

	@Test
	public void testMetadataImmutability() {
		Map<String, Object> originalMetadata = new HashMap<>();
		originalMetadata.put("key", "original");
		float[] embedding = new float[] { 1.0f };

		SimpleVectorStoreContent storeContent = new SimpleVectorStoreContent("id-1", "text", originalMetadata,
				embedding);

		originalMetadata.put("key", "modified");
		originalMetadata.put("new", "value");

		Document document = storeContent.toDocument(0.5);

		assertThat(document.getMetadata().get("key")).isEqualTo("original");
		assertThat(document.getMetadata()).doesNotContainKey("new");
	}

	@Test
	public void testWhitespaceOnlyText() {
		Map<String, Object> metadata = new HashMap<>();
		float[] embedding = new float[] { 1.0f };
		String[] whitespaceTexts = { "   ", "\t\t", "\n\n", "\r\n", "   \t\n\r   " };

		for (String whitespace : whitespaceTexts) {
			SimpleVectorStoreContent storeContent = new SimpleVectorStoreContent("ws-id", whitespace, metadata,
					embedding);
			Document document = storeContent.toDocument(0.1);
			assertThat(document.getText()).isEqualTo(whitespace);
		}
	}

	@Test
	public void testEmptyStringText() {
		Map<String, Object> metadata = new HashMap<>();
		float[] embedding = new float[] { 1.0f };

		SimpleVectorStoreContent storeContent = new SimpleVectorStoreContent("empty-id", "", metadata, embedding);
		Document document = storeContent.toDocument(0.1);

		assertThat(document.getText()).isEmpty();
	}

}
