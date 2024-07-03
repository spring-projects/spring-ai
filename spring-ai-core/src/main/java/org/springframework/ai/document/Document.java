/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.ai.model.Content;
import org.springframework.util.Assert;

/**
 * A document is a container for the content and metadata of a document. It also contains
 * the document's unique ID and an optional embedding.
 */
@JsonIgnoreProperties({ "contentFormatter" })
public class Document implements Content {

	public final static ContentFormatter DEFAULT_CONTENT_FORMATTER = DefaultContentFormatter.defaultConfig();

	/**
	 * Unique ID
	 */
	private final String id;

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private Map<String, Object> metadata;

	/**
	 * Document content.
	 */
	private String content;

	private List<Media> media;

	/**
	 * Embedding of the document. Note: ephemeral field.
	 */
	@JsonProperty(index = 100)
	private List<Double> embedding = new ArrayList<>();

	/**
	 * Mutable, ephemeral, content to text formatter. Defaults to Document text.
	 */
	@JsonIgnore
	private ContentFormatter contentFormatter = DEFAULT_CONTENT_FORMATTER;

	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public Document(@JsonProperty("content") String content) {
		this(content, new HashMap<>());
	}

	public Document(String content, Map<String, Object> metadata) {
		this(content, metadata, new RandomIdGenerator());
	}

	public Document(String content, List<Media> media, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(content, metadata), content, media, metadata);
	}

	public Document(String content, Map<String, Object> metadata, IdGenerator idGenerator) {
		this(idGenerator.generateId(content, metadata), content, metadata);
	}

	public Document(String id, String content, Map<String, Object> metadata) {
		this(id, content, List.of(), metadata);
	}

	public Document(String id, String content, List<Media> media, Map<String, Object> metadata) {
		Assert.hasText(id, "id must not be null or empty");
		Assert.hasText(content, "content must not be null or empty");
		Assert.notNull(metadata, "metadata must not be null");

		this.id = id;
		this.content = content;
		this.media = media;
		this.metadata = metadata;
	}

	public String getId() {
		return id;
	}

	@Override
	public String getContent() {
		return this.content;
	}

	@Override
	public List<Media> getMedia(String... dummy) {
		return this.media;
	}

	@JsonIgnore
	public String getFormattedContent() {
		return this.getFormattedContent(MetadataMode.ALL);
	}

	public String getFormattedContent(MetadataMode metadataMode) {
		Assert.notNull(metadataMode, "Metadata mode must not be null");
		return this.contentFormatter.format(this, metadataMode);
	}

	/**
	 * Helper content extractor that uses and external {@link ContentFormatter}.
	 */
	public String getFormattedContent(ContentFormatter formatter, MetadataMode metadataMode) {
		Assert.notNull(formatter, "formatter must not be null");
		Assert.notNull(metadataMode, "Metadata mode must not be null");
		return formatter.format(this, metadataMode);
	}

	public void setEmbedding(List<Double> embedding) {
		Assert.notNull(embedding, "embedding must not be null");
		this.embedding = embedding;
	}

	/**
	 * Replace the document's {@link ContentFormatter}.
	 * @param contentFormatter new formatter to use.
	 */
	public void setContentFormatter(ContentFormatter contentFormatter) {
		this.contentFormatter = contentFormatter;
	}

	@Override
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	public List<Double> getEmbedding() {
		return this.embedding;
	}

	public ContentFormatter getContentFormatter() {
		return contentFormatter;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
		result = prime * result + ((content == null) ? 0 : content.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Document other = (Document) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		}
		else if (!id.equals(other.id))
			return false;
		if (metadata == null) {
			if (other.metadata != null)
				return false;
		}
		else if (!metadata.equals(other.metadata))
			return false;
		if (content == null) {
			if (other.content != null)
				return false;
		}
		else if (!content.equals(other.content))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Document{" + "id='" + id + '\'' + ", metadata=" + metadata + ", content='" + content + '\'' + ", media="
				+ media + '}';
	}

}
