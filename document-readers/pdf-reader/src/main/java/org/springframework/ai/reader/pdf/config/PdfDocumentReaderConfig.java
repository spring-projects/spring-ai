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

package org.springframework.ai.reader.pdf.config;

import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.util.Assert;

/**
 * Common configuration builder for the {@link PagePdfDocumentReader} and the
 * {@link ParagraphPdfDocumentReader}.
 *
 * @author Christian Tzolov
 */
public final class PdfDocumentReaderConfig {

	public static final int ALL_PAGES = 0;

	public final boolean reversedParagraphPosition;

	public final int pagesPerDocument;

	public final int pageTopMargin;

	public final int pageBottomMargin;

	public final ExtractedTextFormatter pageExtractedTextFormatter;

	private PdfDocumentReaderConfig(PdfDocumentReaderConfig.Builder builder) {
		this.pagesPerDocument = builder.pagesPerDocument;
		this.pageBottomMargin = builder.pageBottomMargin;
		this.pageTopMargin = builder.pageTopMargin;
		this.pageExtractedTextFormatter = builder.pageExtractedTextFormatter;
		this.reversedParagraphPosition = builder.reversedParagraphPosition;
	}

	/**
	 * Start building a new configuration.
	 * @return The entry point for creating a new configuration.
	 */
	public static PdfDocumentReaderConfig.Builder builder() {

		return new Builder();
	}

	/**
	 * {@return the default config}
	 */
	public static PdfDocumentReaderConfig defaultConfig() {
		return builder().build();
	}

	public static final class Builder {

		private int pagesPerDocument = 1;

		private int pageTopMargin = 0;

		private int pageBottomMargin = 0;

		private ExtractedTextFormatter pageExtractedTextFormatter = ExtractedTextFormatter.defaults();

		private boolean reversedParagraphPosition = false;

		private Builder() {
		}

		/**
		 * Formatter of the extracted text.
		 * @param pageExtractedTextFormatter Instance of the PageExtractedTextFormatter.
		 * @return this builder
		 */
		public PdfDocumentReaderConfig.Builder withPageExtractedTextFormatter(
				ExtractedTextFormatter pageExtractedTextFormatter) {
			Assert.notNull(pageExtractedTextFormatter, "PageExtractedTextFormatter must not be null.");
			this.pageExtractedTextFormatter = pageExtractedTextFormatter;
			return this;
		}

		/**
		 * How many pages to put in a single Document instance. 0 stands for all pages.
		 * Defaults to 1.
		 * @param pagesPerDocument Number of page's content to group in single Document.
		 * @return this builder
		 */
		public PdfDocumentReaderConfig.Builder withPagesPerDocument(int pagesPerDocument) {
			Assert.isTrue(pagesPerDocument >= 0, "Page count must be a positive value.");
			this.pagesPerDocument = pagesPerDocument;
			return this;
		}

		/**
		 * Configures the Pdf reader page top margin. Defaults to 0.
		 * @param topMargin page top margin to use
		 * @return this builder
		 */
		public PdfDocumentReaderConfig.Builder withPageTopMargin(int topMargin) {
			Assert.isTrue(topMargin >= 0, "Page margins must be a positive value.");
			this.pageTopMargin = topMargin;
			return this;
		}

		/**
		 * Configures the Pdf reader page bottom margin. Defaults to 0.
		 * @param bottomMargin page top margin to use
		 * @return this builder
		 */
		public PdfDocumentReaderConfig.Builder withPageBottomMargin(int bottomMargin) {
			Assert.isTrue(bottomMargin >= 0, "Page margins must be a positive value.");
			this.pageBottomMargin = bottomMargin;
			return this;
		}

		/**
		 * Configures the Pdf reader reverse paragraph position. Defaults to false.
		 * @param reversedParagraphPosition to reverse or not the paragraph position
		 * withing a page.
		 * @return this builder
		 */
		public Builder withReversedParagraphPosition(boolean reversedParagraphPosition) {
			this.reversedParagraphPosition = reversedParagraphPosition;
			return this;
		}

		/**
		 * {@return the immutable configuration}
		 */
		public PdfDocumentReaderConfig build() {
			return new PdfDocumentReaderConfig(this);
		}

	}

}
