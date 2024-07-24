package org.springframework.ai.reader.markdown.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * Common configuration for the {@link MarkdownDocumentReader}.
 *
 * @author Piotr Olaszewski
 */
public class MarkdownDocumentReaderConfig {

	public final boolean horizontalRuleCreateDocument;

	public final boolean includeCodeBlock;

	public final boolean includeBlockquote;

	public final Map<String, Object> additionalMetadata;

	public MarkdownDocumentReaderConfig(Builder builder) {
		horizontalRuleCreateDocument = builder.horizontalRuleCreateDocument;
		includeCodeBlock = builder.includeCodeBlock;
		includeBlockquote = builder.includeBlockquote;
		additionalMetadata = builder.additionalMetadata;
	}

	/**
	 * @return the default configuration
	 */
	public static MarkdownDocumentReaderConfig defaultConfig() {
		return builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private boolean horizontalRuleCreateDocument = false;

		private boolean includeCodeBlock = false;

		private boolean includeBlockquote = false;

		private Map<String, Object> additionalMetadata = new HashMap<>();

		private Builder() {
		}

		/**
		 * Text divided by horizontal lines will create new {@link Document}s. The default
		 * is {@code false}, meaning text separated by horizontal lines won't create a new
		 * document.
		 * @param horizontalRuleCreateDocument flag to determine whether new documents are
		 * created from text divided by horizontal line
		 * @return this builder
		 */
		public Builder withHorizontalRuleCreateDocument(boolean horizontalRuleCreateDocument) {
			this.horizontalRuleCreateDocument = horizontalRuleCreateDocument;
			return this;
		}

		/**
		 * Whatever to include code blocks in {@link Document}s. The default is
		 * {@code false}, which means all code blocks are in separate documents.
		 * @param includeCodeBlock flag to include code block into paragraph document or
		 * create new with code only
		 * @return this builder
		 */
		public Builder withIncludeCodeBlock(boolean includeCodeBlock) {
			this.includeCodeBlock = includeCodeBlock;
			return this;
		}

		/**
		 * Whatever to include blockquotes in {@link Document}s. The default is
		 * {@code false}, which means all blockquotes are in separate documents.
		 * @param includeBlockquote flag to include blockquotes into paragraph document or
		 * create new with blockquote only
		 * @return this builder
		 */
		public Builder withIncludeBlockquote(boolean includeBlockquote) {
			this.includeBlockquote = includeBlockquote;
			return this;
		}

		/**
		 * Adds this additional metadata to the all built {@link Document}s.
		 * @return this builder
		 */
		public Builder withAdditionalMetadata(String key, Object value) {
			Assert.notNull(key, "key must not be null");
			Assert.notNull(value, "value must not be null");
			this.additionalMetadata.put(key, value);
			return this;
		}

		/**
		 * Adds this additional metadata to the all built {@link Document}s.
		 * @return this builder
		 */
		public Builder withAdditionalMetadata(Map<String, Object> additionalMetadata) {
			Assert.notNull(additionalMetadata, "additionalMetadata must not be null");
			this.additionalMetadata = additionalMetadata;
			return this;
		}

		/**
		 * @return the immutable configuration
		 */
		public MarkdownDocumentReaderConfig build() {
			return new MarkdownDocumentReaderConfig(this);
		}

	}

}
