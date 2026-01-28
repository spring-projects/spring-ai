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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link ContentFormatter}.
 *
 * @author Christian Tzolov
 */
public final class DefaultContentFormatter implements ContentFormatter {

	private static final String TEMPLATE_CONTENT_PLACEHOLDER = "{content}";

	private static final String TEMPLATE_METADATA_STRING_PLACEHOLDER = "{metadata_string}";

	private static final String TEMPLATE_VALUE_PLACEHOLDER = "{value}";

	private static final String TEMPLATE_KEY_PLACEHOLDER = "{key}";

	private static final String DEFAULT_METADATA_TEMPLATE = String.format("%s: %s", TEMPLATE_KEY_PLACEHOLDER,
			TEMPLATE_VALUE_PLACEHOLDER);

	private static final String DEFAULT_METADATA_SEPARATOR = System.lineSeparator();

	private static final String DEFAULT_TEXT_TEMPLATE = String.format("%s\n\n%s", TEMPLATE_METADATA_STRING_PLACEHOLDER,
			TEMPLATE_CONTENT_PLACEHOLDER);

	/**
	 * Template for how metadata is formatted, with {key} and {value} placeholders.
	 */
	private final String metadataTemplate;

	/**
	 * Separator between metadata fields when converting to string.
	 */
	private final String metadataSeparator;

	/**
	 * Template for how Document text is formatted, with {content} and {metadata_string}
	 * placeholders.
	 */
	private final String textTemplate;

	/**
	 * Metadata keys that are excluded from text for the inference.
	 */
	private final List<String> excludedInferenceMetadataKeys;

	/**
	 * Metadata keys that are excluded from text for the embed generative.
	 */
	private final List<String> excludedEmbedMetadataKeys;

	private DefaultContentFormatter(Builder builder) {
		this.metadataTemplate = builder.metadataTemplate;
		this.metadataSeparator = builder.metadataSeparator;
		this.textTemplate = builder.textTemplate;
		this.excludedInferenceMetadataKeys = builder.excludedInferenceMetadataKeys;
		this.excludedEmbedMetadataKeys = builder.excludedEmbedMetadataKeys;
	}

	/**
	 * Start building a new configuration.
	 * @return The entry point for creating a new configuration.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@return the default config}
	 */
	public static DefaultContentFormatter defaultConfig() {

		return builder().build();
	}

	@Override
	public String format(Document document, MetadataMode metadataMode) {

		var metadata = metadataFilter(document.getMetadata(), metadataMode);

		var metadataText = metadata.entrySet()
			.stream()
			.map(metadataEntry -> this.metadataTemplate.replace(TEMPLATE_KEY_PLACEHOLDER, metadataEntry.getKey())
				.replace(TEMPLATE_VALUE_PLACEHOLDER, metadataEntry.getValue().toString()))
			.collect(Collectors.joining(this.metadataSeparator));

		var text = document.getText() != null ? document.getText() : "";
		return this.textTemplate.replace(TEMPLATE_METADATA_STRING_PLACEHOLDER, metadataText)
			.replace(TEMPLATE_CONTENT_PLACEHOLDER, text);
	}

	/**
	 * Filters the metadata by the configured MetadataMode.
	 * @param metadata Document metadata.
	 * @return Returns the filtered by configured mode metadata.
	 */
	private Map<String, Object> metadataFilter(Map<String, Object> metadata, MetadataMode metadataMode) {

		if (metadataMode == MetadataMode.ALL) {
			return metadata;
		}
		if (metadataMode == MetadataMode.NONE) {
			return Collections.emptyMap();
		}

		Set<String> usableMetadataKeys = new HashSet<>(metadata.keySet());

		if (metadataMode == MetadataMode.INFERENCE) {
			usableMetadataKeys.removeAll(this.excludedInferenceMetadataKeys);
		}
		else if (metadataMode == MetadataMode.EMBED) {
			usableMetadataKeys.removeAll(this.excludedEmbedMetadataKeys);
		}

		return metadata.entrySet()
			.stream()
			.filter(e -> usableMetadataKeys.contains(e.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public String getMetadataTemplate() {
		return this.metadataTemplate;
	}

	public String getMetadataSeparator() {
		return this.metadataSeparator;
	}

	public String getTextTemplate() {
		return this.textTemplate;
	}

	public List<String> getExcludedInferenceMetadataKeys() {
		return Collections.unmodifiableList(this.excludedInferenceMetadataKeys);
	}

	public List<String> getExcludedEmbedMetadataKeys() {
		return Collections.unmodifiableList(this.excludedEmbedMetadataKeys);
	}

	public static final class Builder {

		private String metadataTemplate = DEFAULT_METADATA_TEMPLATE;

		private String metadataSeparator = DEFAULT_METADATA_SEPARATOR;

		private String textTemplate = DEFAULT_TEXT_TEMPLATE;

		private List<String> excludedInferenceMetadataKeys = new ArrayList<>();

		private List<String> excludedEmbedMetadataKeys = new ArrayList<>();

		private Builder() {
		}

		public Builder from(DefaultContentFormatter fromFormatter) {
			this.withExcludedEmbedMetadataKeys(fromFormatter.getExcludedEmbedMetadataKeys())
				.withExcludedInferenceMetadataKeys(fromFormatter.getExcludedInferenceMetadataKeys())
				.withMetadataSeparator(fromFormatter.getMetadataSeparator())
				.withMetadataTemplate(fromFormatter.getMetadataTemplate())
				.withTextTemplate(fromFormatter.getTextTemplate());
			return this;
		}

		/**
		 * Configures the Document metadata template.
		 * @param metadataTemplate Metadata template to use.
		 * @return this builder
		 */
		public Builder withMetadataTemplate(String metadataTemplate) {
			Assert.hasText(metadataTemplate, "Metadata Template must not be empty");
			this.metadataTemplate = metadataTemplate;
			return this;
		}

		/**
		 * Configures the Document metadata separator.
		 * @param metadataSeparator Metadata separator to use.
		 * @return this builder
		 */
		public Builder withMetadataSeparator(String metadataSeparator) {
			Assert.notNull(metadataSeparator, "Metadata separator must not be empty");
			this.metadataSeparator = metadataSeparator;
			return this;
		}

		/**
		 * Configures the Document text template.
		 * @param textTemplate Document's content template.
		 * @return this builder
		 */
		public Builder withTextTemplate(String textTemplate) {
			Assert.hasText(textTemplate, "Document's text template must not be empty");
			this.textTemplate = textTemplate;
			return this;
		}

		/**
		 * Configures the excluded Inference metadata keys to filter out from the
		 * generative.
		 * @param excludedInferenceMetadataKeys Excluded inference metadata keys to use.
		 * @return this builder
		 */
		public Builder withExcludedInferenceMetadataKeys(List<String> excludedInferenceMetadataKeys) {
			Assert.notNull(excludedInferenceMetadataKeys, "Excluded inference metadata keys must not be null");
			this.excludedInferenceMetadataKeys = excludedInferenceMetadataKeys;
			return this;
		}

		public Builder withExcludedInferenceMetadataKeys(String... keys) {
			Assert.notNull(keys, "Excluded inference metadata keys must not be null");
			this.excludedInferenceMetadataKeys.addAll(Arrays.asList(keys));
			return this;
		}

		/**
		 * Configures the excluded Embed metadata keys to filter out from the generative.
		 * @param excludedEmbedMetadataKeys Excluded Embed metadata keys to use.
		 * @return this builder
		 */
		public Builder withExcludedEmbedMetadataKeys(List<String> excludedEmbedMetadataKeys) {
			Assert.notNull(excludedEmbedMetadataKeys, "Excluded Embed metadata keys must not be null");
			this.excludedEmbedMetadataKeys = excludedEmbedMetadataKeys;
			return this;
		}

		public Builder withExcludedEmbedMetadataKeys(String... keys) {
			Assert.notNull(keys, "Excluded Embed metadata keys must not be null");
			this.excludedEmbedMetadataKeys.addAll(Arrays.asList(keys));
			return this;
		}

		/**
		 * {@return the immutable configuration}
		 */
		public DefaultContentFormatter build() {
			return new DefaultContentFormatter(this);
		}

	}

}
