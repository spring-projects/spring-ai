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

package org.springframework.ai.reader.pdf;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Tibor Tarnai
 * @author Fu Jian
 */
class PagePdfDocumentReaderTests {

	@Test
	void classpathRead() {

		PagePdfDocumentReader pdfReader = new PagePdfDocumentReader("classpath:/sample1.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageTopMargin(0)
					.withPageBottomMargin(0)
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
						.withNumberOfTopTextLinesToDelete(0)
						.withNumberOfBottomTextLinesToDelete(3)
						.withNumberOfTopPagesToSkipBeforeDelete(0)
						.overrideLineSeparator("\n")
						.build())
					.withPagesPerDocument(1)
					.build());

		List<Document> docs = pdfReader.get();

		assertThat(docs).hasSize(4);

		String allText = docs.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));

		assertThat(allText).doesNotContain(
				List.of("Page  1 of 4", "Page  2 of 4", "Page  3 of 4", "Page  4 of 4", "PDF  Bookmark   Sample"));
	}

	@Test
	void testIndexOutOfBound() {
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.build())
			.get();

		assertThat(documents).hasSize(64);
	}

	@Test
	void testPagesPerDocument() {
		// The test pdf contain 64 pages
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(32)
					.build())
			.get();

		assertThat(documents).hasSize(2);
	}

	@Test
	void testPagesPerDocumentNotDivisible() {
		// The test pdf contain 64 pages
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(3)
					.build())
			.get();

		assertThat(documents).hasSize(22);
	}

	@Test
	void testAllPagesPerDocument() {
		// The test pdf contain 64 pages
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(0) // all pages into one document
					.build())
			.get();

		assertThat(documents).hasSize(1);
	}

	@Test
	void testSinglePageRange() {
		// The test pdf contains 64 pages. Read only pages 10..12.
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.addPageRange(10, 12)
					.build())
			.get();

		assertThat(documents).hasSize(3);
		assertThat(documents).extracting(doc -> doc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))
			.containsExactly(10, 11, 12);
	}

	@Test
	void testMultipleDisjointPageRanges() {
		// The test pdf contains 64 pages. Read pages 1..2 and the last page (64).
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.addPageRange(1, 2)
					.addPageRange(64, 64)
					.build())
			.get();

		assertThat(documents).hasSize(3);
		assertThat(documents).extracting(doc -> doc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))
			.containsExactly(1, 2, 64);
	}

	@Test
	void testPageRangeWithAllPagesPerDocument() {
		// Pages 5..8 grouped into a single document.
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(0) // all selected pages into one document
					.addPageRange(5, 8)
					.build())
			.get();

		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getMetadata()).containsEntry(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, 5)
			.containsEntry(PagePdfDocumentReader.METADATA_END_PAGE_NUMBER, 8);
	}

	@Test
	void testPageRangeUpperBoundClampedToDocument() {
		// Range end exceeds the 64-page document; it should clamp to the last page.
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.addPageRange(60, 1000)
					.build())
			.get();

		assertThat(documents).hasSize(5);
		assertThat(documents).extracting(doc -> doc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))
			.containsExactly(60, 61, 62, 63, 64);
	}

	@Test
	void testOverlappingPageRangesAreDeduplicated() {
		// Overlapping ranges 10..12 and 11..13 should yield the union 10..13.
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.addPageRange(10, 12)
					.addPageRange(11, 13)
					.build())
			.get();

		assertThat(documents).hasSize(4);
		assertThat(documents).extracting(doc -> doc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))
			.containsExactly(10, 11, 12, 13);
	}

	@Test
	void testInvalidPageRangesAreRejected() {
		// startPage below 1
		assertThatThrownBy(() -> PdfDocumentReaderConfig.builder().addPageRange(0, 5))
			.isInstanceOf(IllegalArgumentException.class);

		// negative startPage
		assertThatThrownBy(() -> PdfDocumentReaderConfig.builder().addPageRange(-1, 5))
			.isInstanceOf(IllegalArgumentException.class);

		// endPage before startPage
		assertThatThrownBy(() -> PdfDocumentReaderConfig.builder().addPageRange(5, 3))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testDisjointRangesSplitAtGapWithAllPagesPerDocument() {
		// Pages 1..2 and 64 with all-pages-per-document must NOT merge across the gap
		// into one Document; each contiguous run becomes its own Document so the
		// start/end page metadata stays accurate.
		var documents = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(0) // all selected pages into one document per
												// contiguous run
					.addPageRange(1, 2)
					.addPageRange(64, 64)
					.build())
			.get();

		assertThat(documents).hasSize(2);
		assertThat(documents.get(0).getMetadata()).containsEntry(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, 1)
			.containsEntry(PagePdfDocumentReader.METADATA_END_PAGE_NUMBER, 2);
		// Single-page run: only the start page number is set (no end_page_number).
		assertThat(documents.get(1).getMetadata()).containsEntry(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, 64)
			.doesNotContainKey(PagePdfDocumentReader.METADATA_END_PAGE_NUMBER);
	}

	@Test
	void testFilteredContentMatchesUnfilteredPages() {
		// Guard against excluded page text leaking into the output: the text of a
		// range-filtered read must equal the text of the same pages from a full read.
		var fullDocuments = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.build())
			.get();

		var rangedDocuments = new PagePdfDocumentReader("classpath:/sample2.pdf",
				PdfDocumentReaderConfig.builder()
					.withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
					.withPagesPerDocument(1)
					.addPageRange(10, 12)
					.build())
			.get();

		assertThat(rangedDocuments).extracting(Document::getText)
			.containsExactly(fullDocuments.get(9).getText(), fullDocuments.get(10).getText(),
					fullDocuments.get(11).getText());
	}

}
