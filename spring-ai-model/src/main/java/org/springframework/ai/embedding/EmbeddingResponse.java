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

package org.springframework.ai.embedding;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.model.ModelResponse;
import org.springframework.util.Assert;

/**
 * Embedding response object.
 */
public class EmbeddingResponse implements ModelResponse<Embedding> {

	/**
	 * Embedding data.
	 */
	private final List<Embedding> embeddings;

	/**
	 * Embedding metadata.
	 */
	private final EmbeddingResponseMetadata metadata;

	/**
	 * Creates a new {@link EmbeddingResponse} instance with empty metadata.
	 * @param embeddings the embedding data.
	 */
	public EmbeddingResponse(List<Embedding> embeddings) {
		this(embeddings, new EmbeddingResponseMetadata());
	}

	/**
	 * Creates a new {@link EmbeddingResponse} instance.
	 * @param embeddings the embedding data.
	 * @param metadata the embedding metadata.
	 */
	public EmbeddingResponse(List<Embedding> embeddings, EmbeddingResponseMetadata metadata) {
		this.embeddings = embeddings;
		this.metadata = metadata;
	}

	/**
	 * @return Get the embedding metadata.
	 */
	public EmbeddingResponseMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public Embedding getResult() {
		Assert.notEmpty(this.embeddings, "No embedding data available.");
		return this.embeddings.get(0);
	}

	/**
	 * @return Get the embedding data.
	 */
	@Override
	public List<Embedding> getResults() {
		return this.embeddings;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		EmbeddingResponse that = (EmbeddingResponse) o;
		return Objects.equals(this.embeddings, that.embeddings) && Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.embeddings, this.metadata);
	}

	@Override
	public String toString() {
		return "EmbeddingResult{" + "data=" + this.embeddings + ", metadata=" + this.metadata + '}';
	}

}
