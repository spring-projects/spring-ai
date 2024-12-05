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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.ai.model.Content;
import org.springframework.util.Assert;

/**
 * An immutable {@link Content} implementation representing content, metadata, and its
 * embeddings. This class is thread-safe and all its fields are final and deeply
 * immutable. The embedding vector is required for all instances of this class.
 */
public final class SimpleVectorStoreContent implements Content {

	private final String id;

	private final String content;

	private final Map<String, Object> metadata;

	private final float[] embedding;

	/**
	 * Creates a new instance with the given content, empty metadata, and embedding
	 * vector.
	 * @param content the content text, must not be null
	 * @param embedding the embedding vector, must not be null
	 */
	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public SimpleVectorStoreContent(@JsonProperty("content") String content,
			@JsonProperty("embedding") float[] embedding) {
		this(content, new HashMap<>(), embedding);
	}

	/**
	 * Creates a new instance with the given content, metadata, and embedding vector.
	 * @param content the content text, must not be null
	 * @param metadata the metadata map, must not be null
	 * @param embedding the embedding vector, must not be null
	 */
	public SimpleVectorStoreContent(String content, Map<String, Object> metadata, float[] embedding) {
		this(content, metadata, new RandomIdGenerator(), embedding);
	}

	/**
	 * Creates a new instance with the given content, metadata, custom ID generator, and
	 * embedding vector.
	 * @param content the content text, must not be null
	 * @param metadata the metadata map, must not be null
	 * @param idGenerator the ID generator to use, must not be null
	 * @param embedding the embedding vector, must not be null
	 */
	public SimpleVectorStoreContent(String content, Map<String, Object> metadata, IdGenerator idGenerator,
			float[] embedding) {
		this(idGenerator.generateId(content, metadata), content, metadata, embedding);
	}

	/**
	 * Creates a new instance with all fields specified.
	 * @param id the unique identifier, must not be empty
	 * @param content the content text, must not be null
	 * @param metadata the metadata map, must not be null
	 * @param embedding the embedding vector, must not be null
	 * @throws IllegalArgumentException if any parameter is null or if id is empty
	 */
	public SimpleVectorStoreContent(String id, String content, Map<String, Object> metadata, float[] embedding) {
		Assert.hasText(id, "id must not be null or empty");
		Assert.notNull(content, "content must not be null");
		Assert.notNull(metadata, "metadata must not be null");
		Assert.notNull(embedding, "embedding must not be null");
		Assert.isTrue(embedding.length > 0, "embedding vector must not be empty");

		this.id = id;
		this.content = content;
		this.metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
		this.embedding = Arrays.copyOf(embedding, embedding.length);
	}

	/**
	 * Creates a new instance with an updated embedding vector.
	 * @param embedding the new embedding vector, must not be null
	 * @return a new instance with the updated embedding
	 * @throws IllegalArgumentException if embedding is null or empty
	 */
	public SimpleVectorStoreContent withEmbedding(float[] embedding) {
		Assert.notNull(embedding, "embedding must not be null");
		Assert.isTrue(embedding.length > 0, "embedding vector must not be empty");
		return new SimpleVectorStoreContent(this.id, this.content, this.metadata, embedding);
	}

	public String getId() {
		return this.id;
	}

	@Override
	public String getContent() {
		return this.content;
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
		return Document.builder().id(this.id).content(this.content).metadata(metadata).score(score).build();
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
		return Objects.equals(this.id, that.id) && Objects.equals(this.content, that.content)
				&& Objects.equals(this.metadata, that.metadata) && Arrays.equals(this.embedding, that.embedding);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(this.id);
		result = 31 * result + Objects.hashCode(this.content);
		result = 31 * result + Objects.hashCode(this.metadata);
		result = 31 * result + Arrays.hashCode(this.embedding);
		return result;
	}

	@Override
	public String toString() {
		return "SimpleVectorStoreContent{" + "id='" + this.id + '\'' + ", content='" + this.content + '\''
				+ ", metadata=" + this.metadata + ", embedding=" + Arrays.toString(this.embedding) + '}';
	}

}
