/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.anthropicsdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CitationsConfigParam;
import com.anthropic.models.messages.ContentBlockSource;
import com.anthropic.models.messages.ContentBlockSourceContent;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Builder class for creating citation-enabled documents using the Anthropic Java SDK.
 * Produces SDK {@link DocumentBlockParam} objects directly.
 *
 * <p>
 * Citations allow Claude to reference specific parts of provided documents in its
 * responses. When a citation document is included in a prompt, Claude can cite the source
 * material, and citation metadata (character ranges, page numbers, or content blocks) is
 * returned in the response.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>
 * <b>Plain Text Document:</b>
 *
 * <pre>{@code
 * AnthropicSdkCitationDocument document = AnthropicSdkCitationDocument.builder()
 *     .plainText("The Eiffel Tower was completed in 1889 in Paris, France.")
 *     .title("Eiffel Tower Facts")
 *     .citationsEnabled(true)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>PDF Document:</b>
 *
 * <pre>{@code
 * AnthropicSdkCitationDocument document = AnthropicSdkCitationDocument.builder()
 *     .pdfFile("path/to/document.pdf")
 *     .title("Technical Specification")
 *     .citationsEnabled(true)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Custom Content Blocks:</b>
 *
 * <pre>{@code
 * AnthropicSdkCitationDocument document = AnthropicSdkCitationDocument.builder()
 *     .customContent(
 *         "Fact 1: The Great Wall spans 21,196 kilometers.",
 *         "Fact 2: Construction began in the 7th century BC.",
 *         "Fact 3: It was built to protect Chinese states."
 *     )
 *     .title("Great Wall Facts")
 *     .citationsEnabled(true)
 *     .build();
 * }</pre>
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see Citation
 * @see AnthropicSdkChatOptions#getCitationDocuments()
 */
public final class AnthropicSdkCitationDocument {

	/**
	 * Document types supported by Anthropic Citations API.
	 */
	public enum DocumentType {

		/** Plain text document with character-based citations. */
		PLAIN_TEXT,

		/** PDF document with page-based citations. */
		PDF,

		/** Custom content with user-defined blocks and block-based citations. */
		CUSTOM_CONTENT

	}

	@SuppressWarnings("NullAway.Init")
	private DocumentType type;

	private @Nullable String title;

	private @Nullable String context;

	@SuppressWarnings("NullAway.Init")
	private Object sourceData;

	private boolean citationsEnabled = false;

	private AnthropicSdkCitationDocument() {
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Convert this citation document to an SDK {@link DocumentBlockParam}.
	 * @return configured DocumentBlockParam for the Anthropic API
	 */
	public DocumentBlockParam toDocumentBlockParam() {
		CitationsConfigParam citationsConfig = CitationsConfigParam.builder().enabled(this.citationsEnabled).build();

		DocumentBlockParam.Builder builder = DocumentBlockParam.builder();

		switch (this.type) {
			case PLAIN_TEXT -> builder.textSource((String) this.sourceData);
			case PDF -> {
				String base64Data = Base64.getEncoder().encodeToString((byte[]) this.sourceData);
				builder.source(DocumentBlockParam.Source.ofBase64(Base64PdfSource.builder().data(base64Data).build()));
			}
			case CUSTOM_CONTENT -> {
				@SuppressWarnings("unchecked")
				List<String> textBlocks = (List<String>) this.sourceData;
				List<ContentBlockSourceContent> contentItems = textBlocks.stream()
					.map(text -> ContentBlockSourceContent.ofText(TextBlockParam.builder().text(text).build()))
					.toList();
				builder.source(DocumentBlockParam.Source
					.ofContent(ContentBlockSource.builder().contentOfBlockSource(contentItems).build()));
			}
		}

		builder.citations(citationsConfig);
		if (this.title != null) {
			builder.title(this.title);
		}
		if (this.context != null) {
			builder.context(this.context);
		}

		return builder.build();
	}

	public boolean isCitationsEnabled() {
		return this.citationsEnabled;
	}

	/**
	 * Builder class for AnthropicSdkCitationDocument.
	 */
	public static class Builder {

		private final AnthropicSdkCitationDocument document = new AnthropicSdkCitationDocument();

		/**
		 * Create a plain text document.
		 * @param text the document text content
		 * @return builder for method chaining
		 */
		public Builder plainText(String text) {
			Assert.hasText(text, "Text content cannot be null or empty");
			this.document.type = DocumentType.PLAIN_TEXT;
			this.document.sourceData = text;
			return this;
		}

		/**
		 * Create a PDF document from byte array.
		 * @param pdfBytes the PDF file content as bytes
		 * @return builder for method chaining
		 */
		public Builder pdf(byte[] pdfBytes) {
			Assert.notNull(pdfBytes, "PDF bytes cannot be null");
			Assert.isTrue(pdfBytes.length > 0, "PDF bytes cannot be empty");
			this.document.type = DocumentType.PDF;
			this.document.sourceData = pdfBytes;
			return this;
		}

		/**
		 * Create a PDF document from file path.
		 * @param filePath path to the PDF file
		 * @return builder for method chaining
		 * @throws IOException if file cannot be read
		 */
		public Builder pdfFile(String filePath) throws IOException {
			Assert.hasText(filePath, "File path cannot be null or empty");
			byte[] pdfBytes = Files.readAllBytes(Paths.get(filePath));
			return pdf(pdfBytes);
		}

		/**
		 * Create a custom content document from text blocks.
		 * @param textBlocks variable number of text strings to create content blocks
		 * @return builder for method chaining
		 */
		public Builder customContent(String... textBlocks) {
			Assert.notNull(textBlocks, "Text blocks cannot be null");
			Assert.notEmpty(textBlocks, "Text blocks cannot be empty");
			this.document.type = DocumentType.CUSTOM_CONTENT;
			this.document.sourceData = Arrays.asList(textBlocks);
			return this;
		}

		/**
		 * Set the document title.
		 * @param title document title for reference
		 * @return builder for method chaining
		 */
		public Builder title(String title) {
			this.document.title = title;
			return this;
		}

		/**
		 * Set the document context.
		 * @param context additional context about the document
		 * @return builder for method chaining
		 */
		public Builder context(String context) {
			this.document.context = context;
			return this;
		}

		/**
		 * Enable or disable citations for this document.
		 * @param enabled whether citations should be enabled
		 * @return builder for method chaining
		 */
		public Builder citationsEnabled(boolean enabled) {
			this.document.citationsEnabled = enabled;
			return this;
		}

		/**
		 * Build the AnthropicSdkCitationDocument.
		 * @return configured citation document
		 */
		public AnthropicSdkCitationDocument build() {
			Assert.notNull(this.document.type, "Document type must be specified");
			Assert.notNull(this.document.sourceData, "Document source data must be specified");
			return this.document;
		}

	}

}
