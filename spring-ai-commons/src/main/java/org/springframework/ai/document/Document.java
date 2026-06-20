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

package org.springframework.ai.document;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A document is a container for the content and metadata of a document. It also contains
 * the document's unique ID.
 *
 * <p>
 * A {@code Document} holds either text content or media content, but not both. Text
 * content is accessed via {@link #getText()}, and media content via {@link #getMedia()}.
 * Use {@link #isText()} to determine which type is present.
 *
 * <p>
 * Documents are intended to carry data through Spring AI's ETL pipeline — from ingestion
 * through transformation to storage in a vector store.
 *
 * <h2>JSON serialization and deserialization</h2>
 * <p>
 * {@code Document} is fully Jackson-serializable. The serialized JSON uses the following
 * field names:
 * <ul>
 * <li>{@code id} — the unique document identifier
 * <li>{@code text} — the text content (null for media documents)
 * <li>{@code media} — the media content (null for text documents)
 * <li>{@code metadata} — the metadata map
 * <li>{@code score} — the relevance score, may be null
 * </ul>
 *
 * <p>
 * Deserialization is handled via {@link Builder}. JSON round-trips are fully supported: a
 * serialized {@code Document} can be deserialized back to an equal instance.
 *
 * <p>
 * Example round-trip: <pre>{@code
 * ObjectMapper mapper = JacksonUtils.getDefaultJsonMapper();
 * Document original = Document.builder()
 *     .id("doc-1")
 *     .text("hello world")
 *     .metadata("source", "example")
 *     .score(0.95)
 *     .build();
 *
 * String json = mapper.writeValueAsString(original);
 * // {"id":"doc-1","text":"hello world","media":null,"metadata":{"source":"example"},"score":0.95}
 *
 * Document restored = mapper.readValue(json, Document.class);
 * // restored.equals(original) == true
 * }</pre>
 *
 * <h2>Creating documents</h2>
 * <p>
 * Example of creating a text document: <pre>{@code
 * // Using constructor
 * Document textDoc = new Document("Sample text content", Map.of("source", "user-input"));
 *
 * // Using builder (preferred)
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
 * // Using builder (preferred)
 * Document mediaDoc = Document.builder()
 *     .media(new Media(MediaType.IMAGE_PNG, new byte[] {...}))
 *     .metadata("filename", "sample.png")
 *     .build();
 * }</pre>
 *
 * <p>
 * Example of checking content type and accessing content: <pre>{@code
 * if (document.isText()) {
 *     String text = document.getText();
 *     // Process text content
 * } else {
 *     Media media = document.getMedia();
 *     // Process media content
 * }
 * }</pre>
 */
@JsonDeserialize(builder = Document.Builder.class)
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

	/**
	 * Creates a text document with a generated ID and empty metadata.
	 * @param text the text content of the document
	 */
	public Document(@Nullable String text) {
		this(text, new HashMap<>());
	}

	/**
	 * Creates a text document with a generated ID and the given metadata.
	 * @param text the text content of the document
	 * @param metadata the metadata map; must not be null and must not contain null keys
	 * or values
	 */
	public Document(@Nullable String text, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(), text, null, metadata, null);
	}

	/**
	 * Creates a text document with an explicit ID and the given metadata.
	 * @param id the unique document identifier; must not be null or empty
	 * @param text the text content of the document
	 * @param metadata the metadata map; must not be null and must not contain null keys
	 * or values
	 */
	public Document(String id, @Nullable String text, Map<String, Object> metadata) {
		this(id, text, null, metadata, null);
	}

	/**
	 * Creates a media document with a generated ID and the given metadata.
	 * @param media the media content of the document
	 * @param metadata the metadata map; must not be null and must not contain null keys
	 * or values
	 */
	public Document(@Nullable Media media, Map<String, Object> metadata) {
		this(new RandomIdGenerator().generateId(), null, media, metadata, null);
	}

	/**
	 * Creates a media document with an explicit ID and the given metadata.
	 * @param id the unique document identifier; must not be null or empty
	 * @param media the media content of the document
	 * @param metadata the metadata map; must not be null and must not contain null keys
	 * or values
	 */
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

	/**
	 * Returns the formatted content of this document using {@link MetadataMode#ALL},
	 * including both text and all metadata entries.
	 * <p>
	 * This method is excluded from JSON serialization as it is a computed, ephemeral
	 * representation.
	 * @return the formatted content string
	 * @see #getFormattedContent(MetadataMode)
	 */
	@JsonIgnore
	public String getFormattedContent() {
		return this.getFormattedContent(MetadataMode.ALL);
	}

	/**
	 * Returns the formatted content of this document using the given
	 * {@link MetadataMode}.
	 * @param metadataMode controls which metadata entries are included in the output;
	 * must not be null
	 * @return the formatted content string
	 */
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

	/**
	 * Returns the relevance score associated with this document, if any.
	 * <p>
	 * The score is typically assigned during retrieval and is not set at document
	 * creation time. Use {@link Builder#score(Double)} or {@link #mutate()} to produce a
	 * new document with a score.
	 * @return the relevance score, or null if none has been assigned
	 */
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

	/**
	 * Returns a new {@link Builder} pre-populated with all fields of this document,
	 * allowing selective modification without altering the original.
	 * @return a builder initialised from this document's state
	 */
	public Builder mutate() {
		return new Builder().id(this.id).text(this.text).media(this.media).metadata(this.metadata).score(this.score);
	}

	@Override
	public boolean equals(@Nullable Object o) {
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

	/**
	 * Builder for {@link Document}.
	 *
	 * <p>
	 * Exactly one of {@link #text(String)} or {@link #media(Media)} must be set before
	 * calling {@link #build()}. If no {@link #id(String)} is provided, one is generated
	 * by the configured {@link IdGenerator}.
	 *
	 * <h3>JSON deserialization</h3>
	 * <p>
	 * This builder is used by Jackson to deserialize {@link Document} instances
	 * (configured via {@code @JsonDeserialize(builder = Builder.class)}). Jackson maps
	 * JSON field names to builder methods by name: {@code "id"} → {@link #id},
	 * {@code "text"} → {@link #text}, {@code "metadata"} → {@link #metadata(Map)},
	 * {@code "score"} → {@link #score}.
	 */
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static final class Builder {

		private @Nullable String id;

		private @Nullable String text;

		private @Nullable Media media;

		private Map<String, Object> metadata = new HashMap<>();

		private @Nullable Double score;

		private IdGenerator idGenerator = new RandomIdGenerator();

		/**
		 * Sets the {@link IdGenerator} used to generate the document ID when none is
		 * provided explicitly. Defaults to {@link RandomIdGenerator}.
		 * @param idGenerator the ID generator to use; must not be null
		 * @return the builder instance
		 */
		public Builder idGenerator(IdGenerator idGenerator) {
			Assert.notNull(idGenerator, "idGenerator cannot be null");
			this.idGenerator = idGenerator;
			return this;
		}

		/**
		 * Sets an explicit ID for the document. If not called, an ID is generated
		 * automatically by the configured {@link IdGenerator} at {@link #build()} time.
		 * @param id the unique document identifier; must not be null or empty
		 * @return the builder instance
		 */
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
		@JsonAlias("content")
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

		/**
		 * Replaces the metadata map with the given map.
		 * @param metadata the metadata map; must not be null and must not contain null
		 * keys or values
		 * @return the builder instance
		 */
		public Builder metadata(Map<String, Object> metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			this.metadata = metadata;
			return this;
		}

		/**
		 * Adds a single key-value pair to the metadata map.
		 * @param key the metadata key; must not be null
		 * @param value the metadata value; must not be null
		 * @return the builder instance
		 */
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

		/**
		 * Builds the {@link Document}.
		 * <p>
		 * If no {@link #id(String)} was set, an ID is generated from the text content and
		 * metadata using the configured {@link IdGenerator}. Exactly one of
		 * {@link #text(String)} or {@link #media(Media)} must have been set.
		 * @return a new {@link Document}
		 * @throws IllegalArgumentException if both or neither of text and media are set,
		 * or if the metadata contains null keys or values
		 */
		public Document build() {
			if (!StringUtils.hasText(this.id)) {
				var text = this.text != null ? this.text : "";
				this.id = this.idGenerator.generateId(text, this.metadata);
			}
			return new Document(this.id, this.text, this.media, this.metadata, this.score);
		}

	}

}
