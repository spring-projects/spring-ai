/*
 * Copyright 2023-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

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

	/**
	 * Inclusive, one-based page ranges to read, each stored as a {@code {start, end}}
	 * pair. An empty array means the whole document is read. Currently honored only by
	 * {@link PagePdfDocumentReader}; readers that do not support page ranges reject a
	 * configuration that sets them.
	 */
	private final int[][] pageRanges;

	private PdfDocumentReaderConfig(PdfDocumentReaderConfig.Builder builder) {
		this.pagesPerDocument = builder.pagesPerDocument;
		this.pageBottomMargin = builder.pageBottomMargin;
		this.pageTopMargin = builder.pageTopMargin;
		this.pageExtractedTextFormatter = builder.pageExtractedTextFormatter;
		this.reversedParagraphPosition = builder.reversedParagraphPosition;
		this.pageRanges = builder.pageRanges.toArray(new int[0][]);
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

	/**
	 * Whether the given one-based page number should be read. When no page ranges are
	 * configured every page is read; otherwise a page is read when it falls within any of
	 * the configured inclusive ranges.
	 * @param pageNumber the one-based page number
	 * @return {@code true} if the page should be read
	 */
	public boolean isPageSelected(int pageNumber) {
		if (this.pageRanges.length == 0) {
			return true;
		}
		for (int[] range : this.pageRanges) {
			if (pageNumber >= range[0] && pageNumber <= range[1]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether any page ranges are configured. Readers that do not support page ranges can
	 * use this to reject such a configuration instead of silently ignoring it.
	 * @return {@code true} if at least one page range has been configured
	 */
	public boolean hasPageRanges() {
		return this.pageRanges.length > 0;
	}

	public static final class Builder {

		private int pagesPerDocument = 1;

		private int pageTopMargin = 0;

		private int pageBottomMargin = 0;

		private ExtractedTextFormatter pageExtractedTextFormatter = ExtractedTextFormatter.defaults();

		private boolean reversedParagraphPosition = false;

		private final List<int[]> pageRanges = new ArrayList<>();

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
		 * Restricts reading to only the given inclusive, one-based page range, allowing
		 * callers to skip pages such as covers, tables of contents or appendices. May be
		 * called multiple times to read multiple, possibly disjoint, ranges; their union
		 * is read. A range whose {@code endPage} exceeds the document length is clamped
		 * to the last page. When no range is added (the default), the whole document is
		 * read.
		 * <p>
		 * Note: page ranges are a coarse, manual filter based on physical page numbers
		 * and are intended for cases where the caller already knows which pages to
		 * include. They are currently honored only by {@link PagePdfDocumentReader};
		 * readers that do not support page ranges reject a configuration that sets them.
		 * @param startPage the inclusive, one-based first page to read
		 * @param endPage the inclusive, one-based last page to read
		 * @return this builder
		 */
		public PdfDocumentReaderConfig.Builder addPageRange(int startPage, int endPage) {
			Assert.isTrue(startPage >= 1, "startPage must be a positive (one-based) value.");
			Assert.isTrue(endPage >= startPage, "endPage must be greater than or equal to startPage.");
			this.pageRanges.add(new int[] { startPage, endPage });
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
