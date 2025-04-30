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

package org.springframework.ai.embedding;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.ai.model.ModelResult;

/**
 * Represents a single embedding vector.
 */
public class Embedding implements ModelResult<float[]> {

	private final float[] embedding;

	private final Integer index;

	private final EmbeddingResultMetadata metadata;

	/**
	 * Creates a new {@link Embedding} instance.
	 * @param embedding the embedding vector values.
	 * @param index the embedding index in a list of embeddings.
	 */
	public Embedding(float[] embedding, Integer index) {
		this(embedding, index, EmbeddingResultMetadata.EMPTY);
	}

	/**
	 * Creates a new {@link Embedding} instance.
	 * @param embedding the embedding vector values.
	 * @param index the embedding index in a list of embeddings.
	 * @param metadata the metadata associated with the embedding.
	 */
	public Embedding(float[] embedding, Integer index, EmbeddingResultMetadata metadata) {
		this.embedding = embedding;
		this.index = index;
		this.metadata = metadata;
	}

	/**
	 * @return Get the embedding vector values.
	 */
	@Override
	public float[] getOutput() {
		return this.embedding;
	}

	/**
	 * @return Get the embedding index in a list of embeddings.
	 */
	public Integer getIndex() {
		return this.index;
	}

	/**
	 * @return Get the metadata associated with the embedding.
	 */
	public EmbeddingResultMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Embedding other = (Embedding) o;
		return Arrays.equals(this.embedding, other.embedding) && Objects.equals(this.index, other.index);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(this.embedding), this.index);
	}

	@Override
	public String toString() {
		String message = this.embedding.length == 0 ? "<empty>" : "<has data>";
		return "Embedding{" + "embedding=" + message + ", index=" + this.index + '}';
	}

}
