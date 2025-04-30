/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.reader.jsoup.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.util.Assert;

/**
 * Common configuration for the {@link JsoupDocumentReader}.
 *
 * Provides options for specifying the character encoding, CSS selector, text separator,
 * and whether to extract all text from the body or specific elements, and handling link
 * extraction.
 *
 * @author Alexandros Pappas
 */
public final class JsoupDocumentReaderConfig {

	public final String charset;

	public final String selector;

	public final String separator;

	public final boolean allElements;

	public final boolean groupByElement;

	public final boolean includeLinkUrls;

	public final List<String> metadataTags;

	public final Map<String, Object> additionalMetadata;

	private JsoupDocumentReaderConfig(Builder builder) {
		this.charset = builder.charset;
		this.selector = builder.selector;
		this.separator = builder.separator;
		this.allElements = builder.allElements;
		this.includeLinkUrls = builder.includeLinkUrls;
		this.metadataTags = builder.metadataTags;
		this.groupByElement = builder.groupByElement;
		this.additionalMetadata = builder.additionalMetadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static JsoupDocumentReaderConfig defaultConfig() {
		return builder().build();
	}

	public static final class Builder {

		private String charset = "UTF-8";

		private String selector = "body";

		private String separator = "\n";

		private boolean allElements = false;

		private boolean includeLinkUrls = false;

		private List<String> metadataTags = new ArrayList<>(List.of("description", "keywords"));

		private boolean groupByElement = false;

		private Map<String, Object> additionalMetadata = new HashMap<>();

		private Builder() {
		}

		/**
		 * Sets the character encoding to use for reading the HTML. Defaults to UTF-8.
		 * @param charset The charset to use.
		 * @return This builder.
		 */
		public Builder charset(String charset) {
			this.charset = charset;
			return this;
		}

		/**
		 * Sets the CSS selector to use for extracting elements. Defaults to "body".
		 * @param selector The CSS selector.
		 * @return This builder.
		 */
		public Builder selector(String selector) {
			this.selector = selector;
			return this;
		}

		/**
		 * Sets the separator string to use when joining text from multiple elements.
		 * Defaults to "\n".
		 * @param separator The separator string.
		 * @return This builder.
		 */
		public Builder separator(String separator) {
			this.separator = separator;
			return this;
		}

		/**
		 * Enables extracting text from all elements in the body, creating a single
		 * document. Overrides the selector setting. Defaults to false.
		 * @param allElements True to extract all text, false otherwise.
		 * @return This builder.
		 */
		public Builder allElements(boolean allElements) {
			this.allElements = allElements;
			return this;
		}

		/**
		 * Determines if on the selected element, the content will be read on per-element
		 * base.
		 * @param groupByElement to read text using element as a separator.
		 * @return this builder.
		 */
		public Builder groupByElement(boolean groupByElement) {
			this.groupByElement = groupByElement;
			return this;
		}

		/**
		 * Enables the inclusion of link URLs in the document metadata. Defaults to false.
		 * @param includeLinkUrls True to include link URLs, false otherwise.
		 * @return This builder.
		 */
		public Builder includeLinkUrls(boolean includeLinkUrls) {
			this.includeLinkUrls = includeLinkUrls;
			return this;
		}

		/**
		 * Adds a metadata tag name to extract from the HTML <meta> tags.
		 * @param metadataTag The name of the metadata tag.
		 * @return This builder.
		 */
		public Builder metadataTag(String metadataTag) {
			this.metadataTags.add(metadataTag);
			return this;
		}

		/**
		 * Sets the metadata tags to extract from the HTML <meta> tags. Overwrites any
		 * previously added tags.
		 * @param metadataTags The list of metadata tag names.
		 * @return This builder.
		 */
		public Builder metadataTags(List<String> metadataTags) {
			this.metadataTags = new ArrayList<>(metadataTags);
			return this;
		}

		/**
		 * Adds this additional metadata to the all built
		 * {@link org.springframework.ai.document.Document}s.
		 * @return this builder
		 */
		public Builder additionalMetadata(String key, Object value) {
			Assert.notNull(key, "key must not be null");
			Assert.notNull(value, "value must not be null");
			this.additionalMetadata.put(key, value);
			return this;
		}

		/**
		 * Adds this additional metadata to the all built
		 * {@link org.springframework.ai.document.Document}s.
		 * @return this builder
		 */
		public Builder additionalMetadata(Map<String, Object> additionalMetadata) {
			Assert.notNull(additionalMetadata, "additionalMetadata must not be null");
			this.additionalMetadata = additionalMetadata;
			return this;
		}

		public JsoupDocumentReaderConfig build() {
			return new JsoupDocumentReaderConfig(this);
		}

	}

}
