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

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.util.Assert;

/**
 * A {@link Document} paired with a pre-computed embedding vector, written to a
 * {@link VectorStore} via {@link VectorStore#upsert(java.util.List)} without invoking the
 * store's embedding model.
 * <p>
 * This is the "bring your own vector" entry point: the caller supplies the embedding —
 * computed by a batch API, an external embedding pipeline, or exported from another
 * system — and the store persists it as-is.
 * <p>
 * The embedding array is defensively copied on construction and on access, so the vector
 * held by an instance cannot be mutated after handoff. Equality compares the document and
 * the element-by-element contents of the embedding array; a record's generated
 * {@code equals} would compare the array by reference instead.
 *
 * @param document the document to store; must not be null
 * @param embedding the pre-computed embedding vector; must not be null, must be
 * non-empty, and must contain only finite values (no {@code NaN} or {@code Infinity}).
 * Caller-supplied vectors are validated here because, unlike {@link VectorStore#add},
 * {@code upsert} never re-embeds through a trusted model.
 * @author Soby Chacko
 * @since 2.1.0
 */
public record EmbeddedDocument(Document document, float[] embedding) {

	public EmbeddedDocument {
		Assert.notNull(document, "document must not be null");
		Assert.notNull(embedding, "embedding must not be null");
		Assert.isTrue(embedding.length > 0, "embedding vector must not be empty");
		for (float value : embedding) {
			Assert.isTrue(Float.isFinite(value), () -> "embedding vector must contain only finite values; found "
					+ (Float.isNaN(value) ? "NaN" : "Infinity"));
		}
		embedding = Arrays.copyOf(embedding, embedding.length);
	}

	/**
	 * Returns a copy of the embedding vector. Mutating the returned array does not affect
	 * this instance.
	 * @return a defensive copy of the embedding vector
	 */
	@Override
	public float[] embedding() {
		return Arrays.copyOf(this.embedding, this.embedding.length);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof EmbeddedDocument that)) {
			return false;
		}
		return this.document.equals(that.document) && Arrays.equals(this.embedding, that.embedding);
	}

	@Override
	public int hashCode() {
		return 31 * this.document.hashCode() + Arrays.hashCode(this.embedding);
	}

}
