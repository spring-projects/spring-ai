package org.springframework.ai.reader.pdf.config;

import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.layout.PageExtractedTextFormatter;
import org.springframework.util.Assert;

/**
 * Common configuration builder for the {@link PagePdfDocumentReader} and the
 * {@link ParagraphPdfDocumentReader}.
 *
 * @author Christian Tzolov
 */
public class PdfDocumentReaderConfig {

	public static final int ALL_PAGES = 0;

	public final boolean reversedParagraphPosition;

	public final int pagesPerDocument;

	public final int pageTopMargin;

	public final int pageBottomMargin;

	public final PageExtractedTextFormatter pageExtractedTextFormatter;

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

	private PdfDocumentReaderConfig(PdfDocumentReaderConfig.Builder builder) {
		this.pagesPerDocument = builder.pagesPerDocument;
		this.pageBottomMargin = builder.pageBottomMargin;
		this.pageTopMargin = builder.pageTopMargin;
		this.pageExtractedTextFormatter = builder.pageExtractedTextFormatter;
		this.reversedParagraphPosition = builder.reversedParagraphPosition;
	}

	public static class Builder {

		private int pagesPerDocument = 1;

		private int pageTopMargin = 0;

		private int pageBottomMargin = 0;

		private PageExtractedTextFormatter pageExtractedTextFormatter = PageExtractedTextFormatter.defaults();

		private boolean reversedParagraphPosition = false;

		private Builder() {
		}

		/**
		 * Formatter of the extracted text.
		 * @param pageExtractedTextFormatter Instance of the PageExtractedTextFormatter.
		 * @return this builder
		 */
		public PdfDocumentReaderConfig.Builder withPageExtractedTextFormatter(
				PageExtractedTextFormatter pageExtractedTextFormatter) {
			Assert.notNull(pagesPerDocument >= 0, "PageExtractedTextFormatter must not be null.");
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
