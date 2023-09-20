package org.springframework.ai.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Document {

	/**
	 * Unique ID
	 */
	private final String id;

	@JsonProperty(index = 100)
	private List<Double> embedding = new ArrayList<>();

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private Map<String, Object> metadata;

	// Type; introduce when support images, now only text.

	private String content;

	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public Document(@JsonProperty("content") String content) {
		this(content, new HashMap<>());
	}

	public Document(String content, Map<String, Object> metadata) {
		this(UUID.randomUUID().toString(), content, metadata);
	}

	public Document(String id, String content, Map<String, Object> metadata) {
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

	public String getContent(TextFormatter formatter) {
		return formatter.format(this);
	}

	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	public List<Double> getEmbedding() {
		return this.embedding;
	}

	public void setEmbedding(List<Double> embedding) {
		this.embedding = embedding;
	}

	@Override
	public String toString() {
		return "Document{" + "id='" + id + '\'' + ", metadata=" + metadata + ", content='" + new String(content) + '\''
				+ '}';
	}

}
