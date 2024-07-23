package org.springframework.ai.reader.markdown.config;

/**
 * @author Piotr Olaszewski
 */
public class MarkdownDocumentReaderConfig {

	public final boolean horizontalRuleCreateDocument;

	public MarkdownDocumentReaderConfig(Builder builder) {
		horizontalRuleCreateDocument = builder.horizontalRuleCreateDocument;
	}

	public static MarkdownDocumentReaderConfig defaultConfig() {
		return builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private boolean horizontalRuleCreateDocument = false;

		private Builder() {
		}

		public Builder withHorizontalRuleCreateDocument(boolean horizontalRuleCreateDocument) {
			this.horizontalRuleCreateDocument = horizontalRuleCreateDocument;
			return this;
		}

		public MarkdownDocumentReaderConfig build() {
			return new MarkdownDocumentReaderConfig(this);
		}

	}

}
