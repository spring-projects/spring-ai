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

package org.springframework.ai.anthropic.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.Citation;
import org.springframework.util.Assert;

/**
 * Builder class for creating citation-enabled documents. Provides a fluent API for
 * constructing documents of different types that can be converted to ContentBlocks for
 * the Anthropic API.
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
 * CitationDocument document = CitationDocument.builder()
 *     .plainText("The Eiffel Tower was completed in 1889 in Paris, France.")
 *     .title("Eiffel Tower Facts")
 *     .build();
 *
 * AnthropicChatOptions options = AnthropicChatOptions.builder()
 *     .model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName())
 *     .citationDocuments(document)
 *     .build();
 *
 * ChatResponse response = chatModel.call(new Prompt("When was the Eiffel Tower built?", options));
 *
 * // Citations are available in response metadata
 * List<Citation> citations = (List<Citation>) response.getMetadata().get("citations");
 * }</pre>
 *
 * <p>
 * <b>PDF Document:</b>
 *
 * <pre>{@code
 * CitationDocument document = CitationDocument.builder()
 *     .pdfFile("path/to/document.pdf")
 *     .title("Technical Specification")
 *     .build();
 *
 * // PDF citations include page numbers
 * }</pre>
 *
 * <p>
 * <b>Custom Content Blocks:</b>
 *
 * <pre>{@code
 * CitationDocument document = CitationDocument.builder()
 *     .customContent(
 *         "Fact 1: The Great Wall spans 21,196 kilometers.",
 *         "Fact 2: Construction began in the 7th century BC.",
 *         "Fact 3: It was built to protect Chinese states."
 *     )
 *     .title("Great Wall Facts")
 *     .build();
 *
 * // Custom content citations reference specific content blocks
 * }</pre>
 *
 * @author Soby Chacko
 * @since 1.1.0
 * @see Citation
 * @see AnthropicChatOptions#getCitationDocuments()
 */
public final class CitationDocument {

	/**
	 * Document types supported by Anthropic Citations API. Each type uses different
	 * citation location formats in the response.
	 */
	public enum DocumentType {

		/**
		 * Plain text document with character-based citations. Text is automatically
		 * chunked by sentences and citations return character start/end indices.
		 */
		PLAIN_TEXT,

		/**
		 * PDF document with page-based citations. Content is extracted and chunked from
		 * the PDF, and citations return page start/end numbers.
		 */
		PDF,

		/**
		 * Custom content with user-defined blocks and block-based citations. Content is
		 * provided as explicit blocks, and citations return block start/end indices.
		 */
		CUSTOM_CONTENT

	}

	private DocumentType type;

	private String title;

	private String context;

	private Object sourceData; // String for text, byte[] for PDF, List<ContentBlock> for
								// custom

	private boolean citationsEnabled = false;

	private CitationDocument() {
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Convert this CitationDocument to a ContentBlock for API usage.
	 * @return ContentBlock configured for citations
	 */
	public AnthropicApi.ContentBlock toContentBlock() {
		AnthropicApi.ContentBlock.Source source = createSource();
		return new AnthropicApi.ContentBlock(source, this.title, this.context, this.citationsEnabled, null);
	}

	private AnthropicApi.ContentBlock.Source createSource() {
		return switch (this.type) {
			case PLAIN_TEXT ->
				new AnthropicApi.ContentBlock.Source("text", "text/plain", (String) this.sourceData, null, null);
			case PDF -> {
				String base64Data = Base64.getEncoder().encodeToString((byte[]) this.sourceData);
				yield new AnthropicApi.ContentBlock.Source("base64", "application/pdf", base64Data, null, null);
			}
			case CUSTOM_CONTENT -> {
				@SuppressWarnings("unchecked")
				List<AnthropicApi.ContentBlock> content = (List<AnthropicApi.ContentBlock>) this.sourceData;
				yield new AnthropicApi.ContentBlock.Source("content", null, null, null, content);
			}
		};
	}

	public boolean isCitationsEnabled() {
		return this.citationsEnabled;
	}

	/**
	 * Builder class for CitationDocument.
	 */
	public static class Builder {

		private final CitationDocument document = new CitationDocument();

		/**
		 * Create a plain text document.
		 * @param text The document text content
		 * @return Builder for method chaining
		 */
		public Builder plainText(String text) {
			Assert.hasText(text, "Text content cannot be null or empty");
			this.document.type = DocumentType.PLAIN_TEXT;
			this.document.sourceData = text;
			return this;
		}

		/**
		 * Create a PDF document from byte array.
		 * @param pdfBytes The PDF file content as bytes
		 * @return Builder for method chaining
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
		 * @param filePath Path to the PDF file
		 * @return Builder for method chaining
		 * @throws IOException if file cannot be read
		 */
		public Builder pdfFile(String filePath) throws IOException {
			Assert.hasText(filePath, "File path cannot be null or empty");
			byte[] pdfBytes = Files.readAllBytes(Paths.get(filePath));
			return pdf(pdfBytes);
		}

		/**
		 * Create a custom content document with user-defined blocks.
		 * @param contentBlocks List of content blocks for fine-grained citation control
		 * @return Builder for method chaining
		 */
		public Builder customContent(List<AnthropicApi.ContentBlock> contentBlocks) {
			Assert.notNull(contentBlocks, "Content blocks cannot be null");
			Assert.notEmpty(contentBlocks, "Content blocks cannot be empty");
			this.document.type = DocumentType.CUSTOM_CONTENT;
			this.document.sourceData = new ArrayList<>(contentBlocks);
			return this;
		}

		/**
		 * Create a custom content document from text blocks.
		 * @param textBlocks Variable number of text strings to create content blocks
		 * @return Builder for method chaining
		 */
		public Builder customContent(String... textBlocks) {
			Assert.notNull(textBlocks, "Text blocks cannot be null");
			Assert.notEmpty(textBlocks, "Text blocks cannot be empty");
			List<AnthropicApi.ContentBlock> blocks = Arrays.stream(textBlocks)
				.map(AnthropicApi.ContentBlock::new)
				.collect(Collectors.toList());
			return customContent(blocks);
		}

		/**
		 * Set the document title (optional, not included in citations).
		 * @param title Document title for reference
		 * @return Builder for method chaining
		 */
		public Builder title(String title) {
			this.document.title = title;
			return this;
		}

		/**
		 * Set the document context (optional, not included in citations).
		 * @param context Additional context or metadata about the document
		 * @return Builder for method chaining
		 */
		public Builder context(String context) {
			this.document.context = context;
			return this;
		}

		/**
		 * Enable or disable citations for this document.
		 * @param enabled Whether citations should be enabled
		 * @return Builder for method chaining
		 */
		public Builder citationsEnabled(boolean enabled) {
			this.document.citationsEnabled = enabled;
			return this;
		}

		/**
		 * Build the CitationDocument.
		 * @return Configured CitationDocument
		 */
		public CitationDocument build() {
			Assert.notNull(this.document.type, "Document type must be specified");
			Assert.notNull(this.document.sourceData, "Document source data must be specified");
			return this.document;
		}

	}

}
