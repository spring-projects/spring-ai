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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.ai.model.Media;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A document is a container for the content and metadata of a document. It also contains
 * the document's unique ID and an optional embedding.
 *
 * Either string based text or Media is the content.
 */
@JsonIgnoreProperties({ "contentFormatter" })
public class Document {

	public static final ContentFormatter DEFAULT_CONTENT_FORMATTER = DefaultContentFormatter.defaultConfig();

	/**
	 * Unique ID
	 */
	private final String id;

	/**
	 * Document string content.
	 */
	private final String text;

	/**
	 * Document media content
	 */
	private final Media media;

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

	public Document(String text, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(), text, null, metadata, null);
	}

	public Document(String id, String text, Map<String, Object> metadata) {
		this(id, text, null, metadata, null);
	}

	public Document(Media media, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(), null, media, metadata, null);
	}

	public Document(String id, Media media, Map<String, Object> metadata) {
		this(id, null, media, metadata, null);
	}


	private Document(String id, String text, Media media, Map<String, Object> metadata, @Nullable Double score) {
		Assert.hasText(id, "id cannot be null or empty");
		Assert.notNull(metadata, "metadata cannot be null");
		Assert.noNullElements(metadata.keySet(), "metadata cannot have null keys");
		Assert.noNullElements(metadata.values(), "metadata cannot have null values");
		if (text == null && media == null) {
			throw new IllegalArgumentException("need to specify either text or media");
		}
		if (text != null && media != null) {
			throw new IllegalArgumentException("can not specify both text and media");
		}
		this.id = id;
		this.text = text;
		this.media = media;
		this.metadata = new HashMap<>(metadata);
		this.score = score;
	}



	/**
	 * @deprecated Use builder instead: {@link Document#builder()}.
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public Document(String id, Media media,
					Map<String, Object> metadata, @Nullable Double score) {
		Assert.hasText(id, "id cannot be null or empty");
		Assert.notNull(media, "media cannot be null");
		Assert.notNull(metadata, "metadata cannot be null");
		Assert.noNullElements(metadata.keySet(), "metadata cannot have null keys");
		Assert.noNullElements(metadata.values(), "metadata cannot have null values");

		this.id = id;
		this.text = null;
		this.media = media;
		this.metadata = Collections.unmodifiableMap(metadata);
		this.score = score;
	}


	public static Builder builder() {
		return new Builder();
	}

	public String getId() {
		return this.id;
	}

	@Deprecated
	public String getContent() {
		return this.getText();
	}

	public String getText() {
		return this.text;
	}

	public boolean isText() {
		return this.text != null;
	}


	public Media getMedia() {
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
			.content(this.text)
			.media(this.media)
			.metadata(this.metadata)
			.score(this.score);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}
		Document document = (Document) o;
		return Objects.equals(this.id, document.id) && Objects.equals(this.text, document.text)
				&& Objects.equals(this.media, document.media) && Objects.equals(this.metadata, document.metadata)
				&& Objects.equals(this.score, document.score);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.text, this.media, this.metadata, this.score);
	}

	@Override
	public String toString() {
		return "Document{" + "id='" + this.id + '\'' + ", content='" + this.text + '\''
				+ ", metadata=" + this.metadata + ", score=" + this.score + '}';
	}

	public static class Builder {

		private String id;

		private String text;

		private Media media;

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

		public Builder text(String text) {
			this.text = text;
			return this;
		}

		public Builder content(String text) {
			this.text = text;
			return this;
		}

		public Builder media(Media media) {
			this.media = media;
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
				this.id = this.idGenerator.generateId(this.text, this.metadata); // TODO Review if metadata should be included
			}
			var document = new Document(this.id, this.text, this.media, this.metadata, this.score);
			document.setEmbedding(this.embedding);
			return document;
		}

	}

}
