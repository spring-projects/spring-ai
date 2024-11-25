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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.ai.model.Content;
import org.springframework.util.Assert;

/**
 * A simple {@link Content} object which represents the content, metadata along its
 * embeddings.
 */
public class SimpleVectorStoreContent implements Content {

	/**
	 * Unique ID
	 */
	private final String id;

	/**
	 * Document content.
	 */
	private final String content;

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private Map<String, Object> metadata;

	/**
	 * Embedding of the document. Note: ephemeral field.
	 */
	@JsonProperty(index = 100)
	private float[] embedding = new float[0];

	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public SimpleVectorStoreContent(@JsonProperty("content") String content) {
		this(content, new HashMap<>());
	}

	public SimpleVectorStoreContent(String content, Map<String, Object> metadata) {
		this(content, metadata, new RandomIdGenerator());
	}

	public SimpleVectorStoreContent(String content, Map<String, Object> metadata, IdGenerator idGenerator) {
		this(idGenerator.generateId(content, metadata), content, metadata);
	}

	public SimpleVectorStoreContent(String id, String content, Map<String, Object> metadata) {
		Assert.hasText(id, "id must not be null or empty");
		Assert.notNull(content, "content must not be null");
		Assert.notNull(metadata, "metadata must not be null");

		this.id = id;
		this.content = content;
		this.metadata = metadata;
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

	public float[] getEmbedding() {
		return this.embedding;
	}

	public void setEmbedding(float[] embedding) {
		Assert.notNull(embedding, "embedding must not be null");
		this.embedding = embedding;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
		result = prime * result + ((this.metadata == null) ? 0 : this.metadata.hashCode());
		result = prime * result + ((this.content == null) ? 0 : this.content.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SimpleVectorStoreContent other = (SimpleVectorStoreContent) obj;
		if (this.id == null) {
			if (other.id != null) {
				return false;
			}
		}
		else if (!this.id.equals(other.id)) {
			return false;
		}
		if (this.metadata == null) {
			if (other.metadata != null) {
				return false;
			}
		}
		else if (!this.metadata.equals(other.metadata)) {
			return false;
		}
		if (this.content == null) {
			if (other.content != null) {
				return false;
			}
		}
		else if (!this.content.equals(other.content)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "Document{" + "id='" + this.id + '\'' + ", metadata=" + this.metadata + ", content='" + this.content
				+ '}';
	}

}
