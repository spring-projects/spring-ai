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

package org.springframework.ai.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.MediaContent;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A document is a container for the content and metadata of a document. It also contains
 * the document's unique ID and an optional embedding.
 */
@JsonIgnoreProperties({ "contentFormatter" })
public class Document implements MediaContent {

	public static final ContentFormatter DEFAULT_CONTENT_FORMATTER = DefaultContentFormatter.defaultConfig();

	public static final String EMPTY_TEXT = "";

	/**
	 * Unique ID
	 */
	private final String id;

	/**
	 * Document content.
	 */
	private final String content;

	private final Collection<Media> media;

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private final Map<String, Object> metadata;

	/**
	 * Measure of similarity between the document embedding and the query vector. The
	 * higher the score, the more they are similar. It's the opposite of the distance
	 * measure.
	 */
	@Nullable
	private final Double score;

	/**
	 * Embedding of the document. Note: ephemeral field.
	 */
	@JsonProperty(index = 100)
	private float[] embedding = new float[0];

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

	/**
	 * @deprecated Use builder instead: {@link Document#builder()}.
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public Document(String content, Collection<Media> media, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(content, metadata), content, media, metadata);
	}

	/**
	 * @deprecated Use builder instead: {@link Document#builder()}.
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public Document(String content, Map<String, Object> metadata, IdGenerator idGenerator) {
		this(idGenerator.generateId(content, metadata), content, metadata);
	}

	public Document(String id, String content, Map<String, Object> metadata) {
		this(id, content, List.of(), metadata);
	}

	/**
	 * @deprecated Use builder instead: {@link Document#builder()}.
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public Document(String id, String content, Collection<Media> media, Map<String, Object> metadata) {
		this(id, content, media, metadata, null);
	}

	/**
	 * @deprecated Use builder instead: {@link Document#builder()}.
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public Document(String id, String content, @Nullable Collection<Media> media,
			@Nullable Map<String, Object> metadata, @Nullable Double score) {
		Assert.hasText(id, "id cannot be null or empty");
		Assert.notNull(content, "content cannot be null");
		Assert.notNull(media, "media cannot be null");
		Assert.noNullElements(media, "media cannot have null elements");
		Assert.notNull(metadata, "metadata cannot be null");
		Assert.noNullElements(metadata.keySet(), "metadata cannot have null keys");
		Assert.noNullElements(metadata.values(), "metadata cannot have null values");

		this.id = id;
		this.content = content;
		this.media = media != null ? media : List.of();
		this.metadata = metadata != null ? metadata : new HashMap<>();
		this.score = score;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getId() {
		return this.id;
	}

	@Override
	public String getContent() {
		return this.content;
	}

	@Override
	public Collection<Media> getMedia() {
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

	@Override
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	@Nullable
	public Double getScore() {
		return this.score;
	}

	/**
	 * Return the embedding that were calculated.
	 * @deprecated We are considering getting rid of this, please comment on
	 * https://github.com/spring-projects/spring-ai/issues/1781
	 * @return the embeddings
	 */
	@Deprecated(since = "1.0.0-M4")
	public float[] getEmbedding() {
		return this.embedding;
	}

	public void setEmbedding(float[] embedding) {
		Assert.notNull(embedding, "embedding must not be null");
		this.embedding = embedding;
	}

	/**
	 * Returns the content formatter associated with this document.
	 * @deprecated We are considering getting rid of this, please comment on
	 * https://github.com/spring-projects/spring-ai/issues/1782
	 * @return the current ContentFormatter instance used for formatting the document
	 * content.
	 */
	@Deprecated(since = "1.0.0-M4")
	public ContentFormatter getContentFormatter() {
		return this.contentFormatter;
	}

	/**
	 * Replace the document's {@link ContentFormatter}.
	 * @param contentFormatter new formatter to use.
	 */
	public void setContentFormatter(ContentFormatter contentFormatter) {
		this.contentFormatter = contentFormatter;
	}

	public Builder mutate() {
		return new Builder().id(this.id)
			.content(this.content)
			.media(new ArrayList<>(this.media))
			.metadata(this.metadata)
			.score(this.score);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}
		Document document = (Document) o;
		return Objects.equals(this.id, document.id) && Objects.equals(this.content, document.content)
				&& Objects.equals(this.media, document.media) && Objects.equals(this.metadata, document.metadata)
				&& Objects.equals(this.score, document.score);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.content, this.media, this.metadata, this.score);
	}

	@Override
	public String toString() {
		return "Document{" + "id='" + this.id + '\'' + ", content='" + this.content + '\'' + ", media=" + this.media
				+ ", metadata=" + this.metadata + ", score=" + this.score + '}';
	}

	public static class Builder {

		private String id;

		private String content = Document.EMPTY_TEXT;

		private List<Media> media = new ArrayList<>();

		private Map<String, Object> metadata = new HashMap<>();

		private float[] embedding = new float[0];

		@Nullable
		private Double score;

		private IdGenerator idGenerator = new RandomIdGenerator();

		public Builder idGenerator(IdGenerator idGenerator) {
			Assert.notNull(idGenerator, "idGenerator cannot be null");
			this.idGenerator = idGenerator;
			return this;
		}

		public Builder id(String id) {
			Assert.hasText(id, "id cannot be null or empty");
			this.id = id;
			return this;
		}

		public Builder content(String content) {
			this.content = content;
			return this;
		}

		public Builder media(List<Media> media) {
			this.media.addAll(media);
			return this;
		}

		public Builder media(Media... media) {
			Assert.noNullElements(media, "media cannot contain null elements");
			this.media.addAll(List.of(media));
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public Builder metadata(String key, Object value) {
			this.metadata.put(key, value);
			return this;
		}

		public Builder embedding(float[] embedding) {
			this.embedding = embedding;
			return this;
		}

		public Builder score(@Nullable Double score) {
			this.score = score;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withIdGenerator(IdGenerator idGenerator) {
			return idGenerator(idGenerator);
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withId(String id) {
			return id(id);
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withContent(String content) {
			return content(content);
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withMedia(List<Media> media) {
			return media(media);
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withMedia(Media media) {
			return media(media);
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withMetadata(Map<String, Object> metadata) {
			return metadata(metadata);
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withMetadata(String key, Object value) {
			return metadata(key, value);
		}

		public Document build() {
			if (!StringUtils.hasText(this.id)) {
				this.id = this.idGenerator.generateId(this.content, this.metadata);
			}
			var document = new Document(this.id, this.content, this.media, this.metadata, this.score);
			document.setEmbedding(this.embedding);
			return document;
		}

	}

}
