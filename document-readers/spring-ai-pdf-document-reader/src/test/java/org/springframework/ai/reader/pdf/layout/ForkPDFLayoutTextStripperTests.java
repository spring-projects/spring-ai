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

package org.springframework.ai.reader.pdf.layout;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.text.TextPositionComparator;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ForkPDFLayoutTextStripper}, including the cumulative per-page line
 * budget and the per-region state reset in {@link PDFLayoutTextStripperByArea}.
 *
 * @author zxuhan7
 * @author Dimitar Proynov
 */
class ForkPDFLayoutTextStripperTests {

	private static final float PAGE_WIDTH = 612f;

	private static final float PAGE_HEIGHT = 792f;

	private static final float GLYPH_HEIGHT = 10f;

	/**
	 * Horizontal positions of glyphs laid out as a staircase, each one two points below
	 * the previous one. Neighbours overlap vertically and are therefore compared by their
	 * x coordinate, while glyphs further apart are compared by their y coordinate, which
	 * makes {@link TextPositionComparator} intransitive. Thirty-two elements is the
	 * smallest list size for which TimSort checks the comparator contract, and these
	 * particular coordinates were found by searching for a permutation that it rejects.
	 */
	private static final int[] STAIRCASE_X = { 410, 242, 122, 266, 286, 61, 197, 267, 88, 419, 245, 204, 246, 10, 118,
			495, 313, 424, 430, 112, 438, 114, 421, 258, 240, 144, 84, 157, 193, 340, 5, 19 };

	@Test
	void sortsTextPositionsInReadingOrder() {
		TextPosition topLeft = textPosition(50, 100);
		TextPosition topRight = textPosition(200, 100);
		TextPosition bottom = textPosition(120, 400);
		List<TextPosition> textList = new ArrayList<>(List.of(bottom, topRight, topLeft));

		ForkPDFLayoutTextStripper.sortTextPositionList(textList);

		assertThat(textList).containsExactly(topLeft, topRight, bottom);
	}

	@Test
	void sortsTextPositionsRejectedByTimSort() {
		List<TextPosition> staircase = staircase();
		TextPositionComparator comparator = new TextPositionComparator();

		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new ArrayList<>(staircase).sort(comparator));

		List<TextPosition> textList = new ArrayList<>(staircase);
		ForkPDFLayoutTextStripper.sortTextPositionList(textList);

		assertThat(textList).containsExactlyInAnyOrderElementsOf(staircase).isSortedAccordingTo(comparator);
	}

	@Test
	void totalLineCountAboveBudgetIsRejected() throws IOException {
		Path pdf = Files.createTempFile("layout-", ".pdf");
		try {
			buildPageAboveLineBudget(pdf);

			PagePdfDocumentReader reader = new PagePdfDocumentReader(new FileSystemResource(pdf.toFile()),
					PdfDocumentReaderConfig.builder().build());

			assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class).hasMessageContaining("line");
		}
		finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void totalLineCountWithinBudgetStillExtracts() throws IOException {
		// Exercised directly against the stripper, independent of PagePdfDocumentReader's
		// downstream text post-processing step, which has unrelated behavior of its own
		// with large inputs.
		Path pdf = Files.createTempFile("layout-", ".pdf");
		try {
			buildPageWithinLineBudget(pdf);

			try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
				PDPage page = document.getPage(0);

				PDFLayoutTextStripperByArea stripper = new PDFLayoutTextStripperByArea();
				stripper.addRegion("page", new java.awt.Rectangle(0, 0, 9_000, 9_000));
				stripper.extractRegions(page);

				String text = stripper.getTextForRegion("page");

				assertThat(text).contains("A").contains("B");
			}
		}
		finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void regionsDoNotLeakLinesAcrossEachOther() throws IOException {
		Path pdf = Files.createTempFile("layout-", ".pdf");
		try {
			float pageHeight = PDRectangle.LETTER.getHeight();
			buildTwoBlocksPdf(pdf, pageHeight);

			try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
				PDPage page = document.getPage(0);

				PDFLayoutTextStripperByArea stripper = new PDFLayoutTextStripperByArea();
				Rectangle2D topRegion = new java.awt.Rectangle(0, 0, (int) PDRectangle.LETTER.getWidth(),
						(int) (pageHeight / 2));
				Rectangle2D bottomRegion = new java.awt.Rectangle(0, (int) (pageHeight / 2),
						(int) PDRectangle.LETTER.getWidth(), (int) (pageHeight / 2));
				stripper.addRegion("top", topRegion);
				stripper.addRegion("bottom", bottomRegion);
				stripper.extractRegions(page);

				String topText = stripper.getTextForRegion("top");
				String bottomText = stripper.getTextForRegion("bottom");

				assertThat(topText).contains("TOPTEXT").doesNotContain("BOTTOMTEXT");
				assertThat(bottomText).contains("BOTTOMTEXT").doesNotContain("TOPTEXT");
			}
		}
		finally {
			Files.deleteIfExists(pdf);
		}
	}

	private static List<TextPosition> staircase() {
		List<TextPosition> textList = new ArrayList<>();
		for (int i = 0; i < STAIRCASE_X.length; i++) {
			textList.add(textPosition(STAIRCASE_X[i], i * 2f));
		}
		return textList;
	}

	/**
	 * Creates a text position for a single glyph on an unrotated page, where {@code x}
	 * and {@code y} are the coordinates of its lower left corner with the origin in the
	 * upper left corner of the page. The font is left null because
	 * {@link TextPositionComparator} only reads the glyph geometry.
	 */
	private static TextPosition textPosition(float x, float y) {
		Matrix textMatrix = Matrix.getTranslateInstance(x, PAGE_HEIGHT - y);
		return new TextPosition(0, PAGE_WIDTH, PAGE_HEIGHT, textMatrix, x + 5f, PAGE_HEIGHT - y, GLYPH_HEIGHT, 5f, 3f,
				"a", new int[] { 'a' }, null, 10f, 10);
	}

	private void buildPageAboveLineBudget(Path out) throws IOException {
		buildSparseTextBlocks(out, 9_000, 0.12f, 7, 417);
	}

	private void buildPageWithinLineBudget(Path out) throws IOException {
		buildSparseTextBlocks(out, 9_000, 0.12f, 2, 555);
	}

	/**
	 * Builds a single page containing {@code blockCount} short text blocks, each placed a
	 * fixed vertical distance apart, with an unusually small font relative to the page
	 * size.
	 */
	private void buildSparseTextBlocks(Path out, int pageSide, float fontSize, int blockCount, int verticalSpacing)
			throws IOException {
		try (PDDocument doc = new PDDocument()) {
			PDPage page = new PDPage(new PDRectangle(pageSide, pageSide));
			doc.addPage(page);
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
				for (int i = 0; i < blockCount; i++) {
					cs.beginText();
					cs.setFont(font, fontSize);
					cs.newLineAtOffset(100, 100 + (i * verticalSpacing));
					cs.showText(i % 2 == 0 ? "A" : "B");
					cs.endText();
				}
			}
			doc.save(out.toFile());
		}
	}

	private void buildTwoBlocksPdf(Path out, float pageHeight) throws IOException {
		try (PDDocument doc = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.LETTER);
			doc.addPage(page);
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
				cs.beginText();
				cs.setFont(font, 12f);
				cs.newLineAtOffset(50, pageHeight - 50);
				cs.showText("TOPTEXT");
				cs.endText();

				cs.beginText();
				cs.setFont(font, 12f);
				cs.newLineAtOffset(50, 50);
				cs.showText("BOTTOMTEXT");
				cs.endText();
			}
			doc.save(out.toFile());
		}
	}

}
