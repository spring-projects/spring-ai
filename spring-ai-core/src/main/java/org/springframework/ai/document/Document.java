package org.springframework.ai.document;

import org.springframework.util.StringUtils;

import java.util.*;

public class Document {

	/**
	 * Unique ID
	 */
	private final String id;

	private List<Double> embedding = new ArrayList<>();

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private Map<String, Object> metadata = new HashMap<>();

	// Type; introduce when support images, now only text.

	// TODO: Rename to `content` instead.
	private String text;

	public Document(String text) {
		this(UUID.randomUUID().toString(), text);
	}

	public Document(String id, String text) {
		this.id = id;
		this.text = text;
	}

	public Document(String text, Map<String, Object> metadata) {
		this(UUID.randomUUID().toString(), text, metadata);
	}

	public Document(String id, String text, Map<String, Object> metadata) {
		this.id = id;
		this.text = text;
		this.metadata = metadata;
	}

	public String getId() {
		return id;
	}

	public String getText() {
		return this.text;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public List<Double> getEmbedding() {
		return embedding;
	}

	public void setEmbedding(List<Double> embedding) {
		this.embedding = embedding;
	}

	@Override
	public String toString() {
		return "Document{" + "id='" + id + '\'' + ", metadata=" + metadata + ", text='" + text + '\'' + '}';
	}

	// TODO: Consider moving the following methods & fields in a seprarate
	// dedicated class. (e.g. DocumentService, DocumentUtil or alike)¬

	// private List<String> excludedMetadataKeysForEmbedding;
	// private List<String> relatedIds;

	private static String DEFAULT_TEXT_TEMPLATE = "{metadata_string}\n\n{text}";

	private static String DEFAULT_METADATA_TEMPLATE = "{key}: {value}";

	private final String textTemplate = DEFAULT_TEXT_TEMPLATE;

	private final String metadataTemplate = DEFAULT_METADATA_TEMPLATE;

	private final String metadataSeparator = "\n";

	private MetadataMode metadataMode = MetadataMode.NONE;

	private List<String> excludedMetadataKeysForLlm;

	public String getContent() {
		return getContent(MetadataMode.ALL);
	}

	public String getContent(MetadataMode metadataMode) {
		if (metadataMode == MetadataMode.NONE) {
			return this.text;
		}
		String metadataString = getMetadataString(metadataMode);
		if (!StringUtils.hasText(metadataString)) {
			return this.text;
		}
		return getTextTemplate().replace("{metadata_string}", metadataString).replace("{text}", text);
	}

	public String getMetadataString() {
		return getMetadataString(metadataMode);
	}

	public String getMetadataString(MetadataMode metadataMode) {
		if (metadataMode == MetadataMode.NONE) {
			return "";
		}
		Set<String> usableMetadataKeys = new HashSet<>(metadata.keySet());
		if (metadataMode == MetadataMode.LLM) {
			usableMetadataKeys.removeAll(this.excludedMetadataKeysForLlm);
		}
		else if (metadataMode == MetadataMode.EMBED) {
			usableMetadataKeys.removeAll(this.excludedMetadataKeysForLlm);
		}

		List<String> metadataStringList = new ArrayList<>();

		for (Map.Entry<String, Object> entry : metadata.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (usableMetadataKeys.contains(key)) {
				metadataStringList
					.add(getMetadataTemplate().replace("{key}", key).replace("{value}", value.toString()));
			}
		}
		return String.join(getMetadataSeparator(), metadataStringList);
	}

	private String getTextTemplate() {
		return textTemplate;
	}

	private String getMetadataTemplate() {
		return metadataTemplate;
	}

	private String getMetadataSeparator() {
		return metadataSeparator;
	}

	// public void setTextTemplate(String textTemplate) {
	// this.textTemplate = textTemplate;
	// }

	// public void setMetadataTemplate(String metadataTemplate) {
	// this.metadataTemplate = metadataTemplate;
	// }

	// public void setMetadataSeparator(String metadataSeparator) {
	// this.metadataSeparator = metadataSeparator;
	// }

}
