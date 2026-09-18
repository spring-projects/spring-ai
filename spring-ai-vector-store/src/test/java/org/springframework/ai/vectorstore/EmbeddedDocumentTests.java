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

package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EmbeddedDocument}.
 *
 * @author Soby Chacko
 */
class EmbeddedDocumentTests {

	private static Document doc(String id) {
		return Document.builder().id(id).text("content of " + id).build();
	}

	@Test
	void constructorCopiesEmbedding() {
		float[] source = { 0.1f, 0.2f, 0.3f };
		EmbeddedDocument entry = new EmbeddedDocument(doc("a"), source);

		// Mutating the source array after handoff must not change the stored vector.
		source[0] = 9.9f;

		assertThat(entry.embedding()).containsExactly(0.1f, 0.2f, 0.3f);
	}

	@Test
	void accessorReturnsCopy() {
		EmbeddedDocument entry = new EmbeddedDocument(doc("a"), new float[] { 0.1f, 0.2f, 0.3f });

		// Mutating the returned array must not change the stored vector.
		entry.embedding()[0] = 9.9f;

		assertThat(entry.embedding()).containsExactly(0.1f, 0.2f, 0.3f);
	}

	@Test
	void equalsAndHashCodeCompareEmbeddingByContents() {
		// Distinct array instances holding the same values are equal.
		EmbeddedDocument one = new EmbeddedDocument(doc("a"), new float[] { 0.1f, 0.2f });
		EmbeddedDocument two = new EmbeddedDocument(doc("a"), new float[] { 0.1f, 0.2f });

		assertThat(one).isEqualTo(two);
		assertThat(one).hasSameHashCodeAs(two);
	}

	@Test
	void notEqualWhenEmbeddingDiffers() {
		EmbeddedDocument one = new EmbeddedDocument(doc("a"), new float[] { 0.1f, 0.2f });
		EmbeddedDocument two = new EmbeddedDocument(doc("a"), new float[] { 0.1f, 0.9f });

		assertThat(one).isNotEqualTo(two);
	}

	@Test
	void notEqualWhenDocumentDiffers() {
		EmbeddedDocument one = new EmbeddedDocument(doc("a"), new float[] { 0.1f, 0.2f });
		EmbeddedDocument two = new EmbeddedDocument(doc("b"), new float[] { 0.1f, 0.2f });

		assertThat(one).isNotEqualTo(two);
	}

	@Test
	void nullDocumentThrows() {
		assertThatThrownBy(() -> new EmbeddedDocument(null, new float[] { 0.1f }))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("document must not be null");
	}

	@Test
	void nullEmbeddingThrows() {
		assertThatThrownBy(() -> new EmbeddedDocument(doc("a"), null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embedding must not be null");
	}

	@Test
	void emptyEmbeddingThrows() {
		assertThatThrownBy(() -> new EmbeddedDocument(doc("a"), new float[0]))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embedding vector must not be empty");
	}

	@Test
	void nanEmbeddingThrows() {
		assertThatThrownBy(() -> new EmbeddedDocument(doc("a"), new float[] { 0.1f, Float.NaN, 0.3f }))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("NaN");
	}

	@Test
	void infiniteEmbeddingThrows() {
		assertThatThrownBy(() -> new EmbeddedDocument(doc("a"), new float[] { 0.1f, Float.POSITIVE_INFINITY }))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Infinity");

		assertThatThrownBy(() -> new EmbeddedDocument(doc("a"), new float[] { Float.NEGATIVE_INFINITY, 0.2f }))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Infinity");
	}

}
