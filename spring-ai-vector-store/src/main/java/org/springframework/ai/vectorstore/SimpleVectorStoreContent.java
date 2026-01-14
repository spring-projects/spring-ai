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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Content;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.util.Assert;

/**
 * An immutable {@link Content} implementation representing content, metadata, and its
 * embeddings. This class is thread-safe and all its fields are final and deeply
 * immutable. The embedding vector is required for all instances of this class.
 */
public final class SimpleVectorStoreContent implements Content {

	private final String id;

	private final String text;

	private final Map<String, Object> metadata;

	private final float[] embedding;

	/**
	 * Creates a new instance with the given content, empty metadata, and embedding
	 * vector.
	 * @param text the content text, must not be null
	 * @param embedding the embedding vector, must not be null
	 */
	public SimpleVectorStoreContent(@JsonProperty("text") @JsonAlias("content") String text,
			@JsonProperty("embedding") float[] embedding) {
		this(text, new HashMap<>(), embedding);
	}

	/**
	 * Creates a new instance with the given content, metadata, and embedding vector.
	 * @param text the content text, must not be null
	 * @param metadata the metadata map, must not be null
	 * @param embedding the embedding vector, must not be null
	 */
	public SimpleVectorStoreContent(String text, Map<String, Object> metadata, float[] embedding) {
		this(text, metadata, new RandomIdGenerator(), embedding);
	}

	/**
	 * Creates a new instance with the given content, metadata, custom ID generator, and
	 * embedding vector.
	 * @param text the content text, must not be null
	 * @param metadata the metadata map, must not be null
	 * @param idGenerator the ID generator to use, must not be null
	 * @param embedding the embedding vector, must not be null
	 */
	public SimpleVectorStoreContent(String text, Map<String, Object> metadata, IdGenerator idGenerator,
			float[] embedding) {
		this(idGenerator.generateId(text, metadata), text, metadata, embedding);
	}

	/**
	 * Creates a new instance with all fields specified.
	 * @param id the unique identifier, must not be empty
	 * @param text the content text, must not be null
	 * @param metadata the metadata map, must not be null
	 * @param embedding the embedding vector, must not be null
	 * @throws IllegalArgumentException if any parameter is null or if id is empty
	 */
	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public SimpleVectorStoreContent(@JsonProperty("id") @Nullable String id,
			@JsonProperty("text") @JsonAlias("content") String text,
			@JsonProperty("metadata") Map<String, Object> metadata, @JsonProperty("embedding") float[] embedding) {

		if (id != null) {
			Assert.hasText(id, "id must not be null or empty");
		}
		Assert.notNull(text, "content must not be null");
		Assert.notNull(metadata, "metadata must not be null");
		Assert.notNull(embedding, "embedding must not be null");
		Assert.isTrue(embedding.length > 0, "embedding vector must not be empty");

		this.id = (id != null ? id : new RandomIdGenerator().generateId(text, metadata));
		this.text = text;
		this.metadata = Map.copyOf(metadata);
		this.embedding = Arrays.copyOf(embedding, embedding.length);
	}

	public String getId() {
		return this.id;
	}

	@Override
	public String getText() {
		return this.text;
	}

	@Override
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * Returns a defensive copy of the embedding vector.
	 * @return a new array containing the embedding vector
	 */
	public float[] getEmbedding() {
		return Arrays.copyOf(this.embedding, this.embedding.length);
	}

	public Document toDocument(Double score) {
		var metadata = new HashMap<>(this.metadata);
		metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - score);
		return Document.builder().id(this.id).text(this.text).metadata(metadata).score(score).build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SimpleVectorStoreContent that = (SimpleVectorStoreContent) o;
		return Objects.equals(this.id, that.id) && Objects.equals(this.text, that.text)
				&& Objects.equals(this.metadata, that.metadata) && Arrays.equals(this.embedding, that.embedding);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(this.id);
		result = 31 * result + Objects.hashCode(this.text);
		result = 31 * result + Objects.hashCode(this.metadata);
		result = 31 * result + Arrays.hashCode(this.embedding);
		return result;
	}

	@Override
	public String toString() {
		return "SimpleVectorStoreContent{" + "id='" + this.id + '\'' + ", content='" + this.text + '\'' + ", metadata="
				+ this.metadata + ", embedding=" + Arrays.toString(this.embedding) + '}';
	}

}
