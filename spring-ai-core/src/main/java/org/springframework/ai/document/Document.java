package org.springframework.ai.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.util.Assert;

@JsonIgnoreProperties({ "contentFormatter" })
public class Document {

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

	// Type; introduce when support images, now only text.

	/**
	 * Document content. TODO: To support binary content (image, audio ...)
	 *
	 * - One option is to change the content type to byte[]. Another option is to use
	 * generics, e.g. Document<String> vs Document<byte[]>
	 */
	private String content;

	/**
	 * TODO: do we need the embedding field in the Document? Currently it is used only for
	 * by the InMemoryVectorStore.
	 *
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
		this(UUID.randomUUID().toString(), content, metadata);
	}

	public Document(String id, String content, Map<String, Object> metadata) {
		Assert.hasText(id, "id must not be null");
		Assert.hasText(content, "content must not be null");
		Assert.notNull(metadata, "metadata must not be null");

		this.id = id;
		this.content = content;
		this.metadata = metadata;
	}

	public String getId() {
		return id;
	}

	public String getContent() {
		return this.content;
	}

	@JsonIgnore
	public String getFormattedContent() {
		return this.contentFormatter.apply(this);
	}

	/**
	 * Helper content extractor that uses and external {@link ContentFormatter}.
	 */
	public String getFormatterContent(ContentFormatter formatter) {
		Assert.notNull(formatter, "formatter must not be null");
		return formatter.apply(this);
	}

	public void setEmbedding(List<Double> embedding) {
		Assert.notNull(embedding, "embedding must not be null");
		this.embedding = embedding;
	}

	/**
	 * Replace the document's {@link ContentFormatter}.
	 * @param contentFormatter new formatter to use.
	 * @return Returns an instance of this document with the the updated content
	 * formatter.
	 */
	public Document updateContentFormatter(ContentFormatter contentFormatter) {
		this.contentFormatter = contentFormatter;
		return this;
	}

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
	public String toString() {
		return "Document{" + "id='" + id + '\'' + ", metadata=" + metadata + ", content='" + new String(content) + '\''
				+ '}';
	}

}
