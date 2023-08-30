package org.springframework.ai.document;

import org.springframework.util.StringUtils;

import java.util.*;

public class Document {

	private static String DEFAULT_TEXT_TEMPLATE = "{metadata_string}\n\n{text}";

	private static String DEFAULT_METADATA_TEMPLATE = "{key}: {value}";

	/**
	 * Unique ID
	 */
	private String id = UUID.randomUUID().toString();

	private List<Double> embedding = new ArrayList<>();

	/**
	 * Metadata for the document. It should not be nested and values should be restricted
	 * to string, int, float, boolean for simple use with Vector Dbs.
	 */
	private Map<String, Object> metadata = new HashMap<>();

	// Type; introduce when support images, now only text.

	private String text;

	private MetadataMode metadataMode = MetadataMode.NONE;

	private List<String> excludedMetadataKeysForEmbedding;

	private List<String> excludedMetadataKeysForLlm;

	private List<String> relatedIds;

	private String textTemplate = DEFAULT_TEXT_TEMPLATE;

	private String metadataTemplate = DEFAULT_METADATA_TEMPLATE;

	private String metadataSeparator = "\n";

	public Document(String text) {
		this.text = text;
		this.metadata = metadata;
	}

	public Document(String text, Map<String, Object> metadata) {
		this.text = text;
		this.metadata = metadata;
	}

	public String getId() {
		return id;
	}

	public String getText() {
		return this.text;
	}

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

	public String getTextTemplate() {
		return textTemplate;
	}

	public String getMetadataTemplate() {
		return metadataTemplate;
	}

	public String getMetadataSeparator() {
		return metadataSeparator;
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

	public void setTextTemplate(String textTemplate) {
		this.textTemplate = textTemplate;
	}

	public void setMetadataTemplate(String metadataTemplate) {
		this.metadataTemplate = metadataTemplate;
	}

	public void setMetadataSeparator(String metadataSeparator) {
		this.metadataSeparator = metadataSeparator;
	}

}
