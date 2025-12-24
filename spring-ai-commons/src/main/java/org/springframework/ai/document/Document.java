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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A document is a container for the content and metadata of a document. It also contains
 * the document's unique ID.
 *
 * A Document can hold either text content or media content, but not both.
 *
 * It is intended to be used to take data from external sources as part of spring-ai's ETL
 * pipeline.
 *
 * <p>
 * Example of creating a text document: <pre>{@code
 * // Using constructor
 * Document textDoc = new Document("Sample text content", Map.of("source", "user-input"));
 *
 * // Using builder
 * Document textDoc = Document.builder()
 *     .text("Sample text content")
 *     .metadata("source", "user-input")
 *     .build();
 * }</pre>
 *
 * <p>
 * Example of creating a media document: <pre>{@code
 * // Using constructor
 * Media imageContent = new Media(MediaType.IMAGE_PNG, new byte[] {...});
 * Document mediaDoc = new Document(imageContent, Map.of("filename", "sample.png"));
 *
 * // Using builder
 * Document mediaDoc = Document.builder()
 *     .media(new Media(MediaType.IMAGE_PNG, new byte[] {...}))
 *     .metadata("filename", "sample.png")
 *     .build();
 * }</pre>
 *
 * <p>
 * Example of checking content type and accessing content: <pre>{@code
 * if (document.isText()) {
 *     String textContent = document.getText();
 *     // Process text content
 * } else {
 *     Media mediaContent = document.getMedia();
 *     // Process media content
 * }
 * }</pre>
 */
@JsonIgnoreProperties({ "contentFormatter", "embedding" })
public class Document {

	public static final ContentFormatter DEFAULT_CONTENT_FORMATTER = DefaultContentFormatter.defaultConfig();

	/**
	 * Unique ID
	 */
	private final String id;

	/**
	 * Document string content.
	 */
	private final @Nullable String text;

	/**
	 * Document media content
	 */
	private final @Nullable Media media;

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private final Map<String, Object> metadata;

	/**
	 * A numeric score associated with this document that can represent various types of
	 * relevance measures.
	 * <p>
	 * Common uses include:
	 * <ul>
	 * <li>Measure of similarity between the embedding value of the document's text/media
	 * and a query vector, where higher scores indicate greater similarity (opposite of
	 * distance measure)
	 * <li>Text relevancy rankings from retrieval systems
	 * <li>Custom relevancy metrics from RAG patterns
	 * </ul>
	 * <p>
	 * Higher values typically indicate greater relevance or similarity.
	 */
	private final @Nullable Double score;

	/**
	 * Mutable, ephemeral, content to text formatter. Defaults to Document text.
	 */
	@JsonIgnore
	private ContentFormatter contentFormatter = DEFAULT_CONTENT_FORMATTER;

	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public Document(@JsonProperty("content") @Nullable String content) {
		this(content, new HashMap<>());
	}

	public Document(@Nullable String text, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(), text, null, metadata, null);
	}

	public Document(String id, @Nullable String text, Map<String, Object> metadata) {
		this(id, text, null, metadata, null);
	}

	public Document(@Nullable Media media, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(), null, media, metadata, null);
	}

	public Document(String id, @Nullable Media media, Map<String, Object> metadata) {
		this(id, null, media, metadata, null);
	}

	private Document(String id, @Nullable String text, @Nullable Media media, Map<String, Object> metadata,
			@Nullable Double score) {
		Assert.hasText(id, "id cannot be null or empty");
		Assert.notNull(metadata, "metadata cannot be null");
		Assert.noNullElements(metadata.keySet(), "metadata cannot have null keys");
		Assert.noNullElements(metadata.values(), "metadata cannot have null values");
		Assert.isTrue(text != null ^ media != null, "exactly one of text or media must be specified");

		this.id = id;
		this.text = text;
		this.media = media;
		this.metadata = new HashMap<>(metadata);
		this.score = score;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the unique identifier for this document.
	 * <p>
	 * This ID is either explicitly provided during document creation or generated using
	 * the configured {@link IdGenerator} (defaults to {@link RandomIdGenerator}).
	 * @return the unique identifier of this document
	 * @see RandomIdGenerator
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Returns the document's text content, if any.
	 * @return the text content if {@link #isText()} is true, null otherwise
	 * @see #isText()
	 * @see #getMedia()
	 */
	public @Nullable String getText() {
		return this.text;
	}

	/**
	 * Determines whether this document contains text or media content.
	 * @return true if this document contains text content (accessible via
	 * {@link #getText()}), false if it contains media content (accessible via
	 * {@link #getMedia()})
	 */
	public boolean isText() {
		return this.text != null;
	}

	/**
	 * Returns the document's media content, if any.
	 * @return the media content if {@link #isText()} is false, null otherwise
	 * @see #isText()
	 * @see #getText()
	 */
	public @Nullable Media getMedia() {
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

	/**
	 * Returns the metadata associated with this document.
	 * <p>
	 * The metadata values are restricted to simple types (string, int, float, boolean)
	 * for compatibility with Vector Databases.
	 * @return the metadata map
	 */
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	public @Nullable Double getScore() {
		return this.score;
	}

	/**
	 * Returns the content formatter associated with this document.
	 * @return the current ContentFormatter instance used for formatting the document
	 * content.
	 */
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
		return new Builder().id(this.id).text(this.text).media(this.media).metadata(this.metadata).score(this.score);
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
		return "Document{" + "id='" + this.id + '\'' + ", text='" + this.text + '\'' + ", media='" + this.media + '\''
				+ ", metadata=" + this.metadata + ", score=" + this.score + '}';
	}

	public static final class Builder {

		private @Nullable String id;

		private @Nullable String text;

		private @Nullable Media media;

		private Map<String, Object> metadata = new HashMap<>();

		private @Nullable Double score;

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

		/**
		 * Sets the text content of the document.
		 * <p>
		 * Either text or media content must be set before building the document, but not
		 * both.
		 * @param text the text content
		 * @return the builder instance
		 * @see #media(Media)
		 */
		public Builder text(@Nullable String text) {
			this.text = text;
			return this;
		}

		/**
		 * Sets the media content of the document.
		 * <p>
		 * Either text or media content must be set before building the document, but not
		 * both.
		 * @param media the media content
		 * @return the builder instance
		 * @see #text(String)
		 */
		public Builder media(@Nullable Media media) {
			this.media = media;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			this.metadata = metadata;
			return this;
		}

		public Builder metadata(String key, Object value) {
			Assert.notNull(key, "metadata key cannot be null");
			Assert.notNull(value, "metadata value cannot be null");
			this.metadata.put(key, value);
			return this;
		}

		/**
		 * Sets a score value for this document.
		 * <p>
		 * Common uses include:
		 * <ul>
		 * <li>Measure of similarity between the embedding value of the document's
		 * text/media and a query vector, where higher scores indicate greater similarity
		 * (opposite of distance measure)
		 * <li>Text relevancy rankings from retrieval systems
		 * <li>Custom relevancy metrics from RAG patterns
		 * </ul>
		 * <p>
		 * Higher values typically indicate greater relevance or similarity.
		 * @param score the document score, may be null
		 * @return the builder instance
		 */
		public Builder score(@Nullable Double score) {
			this.score = score;
			return this;
		}

		public Document build() {
			if (!StringUtils.hasText(this.id)) {
				var text = this.text != null ? this.text : "";
				this.id = this.idGenerator.generateId(text, this.metadata);
			}
			return new Document(this.id, this.text, this.media, this.metadata, this.score);
		}

	}

}
