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

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for gh-5829. Consecutive whitespace {@code TextPosition}s with
 * {@code height == 0.0} separated by more than 5.5 points along the Y axis used to cause
 * a division by zero inside
 * {@code ForkPDFLayoutTextStripper.getNumberOfNewLinesFromPreviousTextPosition}, which
 * yielded {@code Double.POSITIVE_INFINITY}, cast to {@code Integer.MAX_VALUE} and
 * triggered an allocation of up to ~400 GiB worth of line buffers.
 *
 * @author Bapuji Koraganti
 */
class PagePdfDocumentReaderOomTests {

	@TempDir(cleanup = CleanupMode.ON_SUCCESS)
	Path workingDir;

	@Test
	void readsPdfWithOffsetZeroHeightWhitespaceWithoutOom() throws IOException {
		Path pdf = this.workingDir.resolve("offset-whitespace.pdf");
		generateOffsetWhitespacePdf(pdf);

		PagePdfDocumentReader reader = new PagePdfDocumentReader(new FileSystemResource(pdf));
		assertThatCode(reader::get).doesNotThrowAnyException();
	}

	/**
	 * Minimal reproducer distilled from the original report: two consecutive whitespace
	 * glyphs rendered with a zero-size font and separated by more than 5.5 points along
	 * the Y axis. Matches the pattern the reporter found on the authors section of
	 * https://www.clinical-lung-cancer.com/article/S1525-7304(22)00115-2/fulltext.
	 */
	static void generateOffsetWhitespacePdf(Path path) throws IOException {
		try (PDDocument doc = new PDDocument()) {
			PDPage page = new PDPage();
			doc.addPage(page);

			PDType1Font mainFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			PDType1Font italicFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

			try (PDPageContentStream contents = new PDPageContentStream(doc, page)) {
				contents.beginText();
				contents.setFont(boldFont, 22);
				contents.newLineAtOffset(50, 750);
				contents.showText("A Study on AI-Driven PDF Parsing");
				contents.endText();

				contents.beginText();
				contents.setFont(mainFont, 12);
				contents.newLineAtOffset(50, 720);
				contents.showText("John Doe");

				contents.setTextMatrix(Matrix.getTranslateInstance(110, 728));
				contents.showText("1");

				// Trigger: first whitespace at superscript baseline, height == 0.0.
				contents.setFont(mainFont, 0);
				contents.showText(" ");
				contents.endText();

				// Trigger: second whitespace, > 5.5 points above the previous Y.
				contents.beginText();
				contents.setFont(mainFont, 0);
				contents.setTextMatrix(Matrix.getTranslateInstance(120, 740));
				contents.showText(" ");
				contents.endText();

				contents.beginText();
				contents.setFont(mainFont, 12);
				contents.setTextMatrix(Matrix.getTranslateInstance(135, 720));
				contents.showText("Jane Smith");

				contents.setTextMatrix(Matrix.getTranslateInstance(200, 728));
				contents.showText("2");
				contents.endText();

				contents.beginText();
				contents.setFont(mainFont, 0);
				contents.showText(" ");
				contents.endText();

				contents.beginText();
				contents.setFont(italicFont, 9);
				contents.newLineAtOffset(50, 150);
				contents.showText("1. Department of Large Integers, Spring AI University");
				contents.endText();

				contents.beginText();
				contents.setFont(italicFont, 9);
				contents.newLineAtOffset(50, 135);
				contents.showText("2. Division of Divide-by-Zero, Overflow Institute");
				contents.endText();

				contents.beginText();
				contents.setFont(mainFont, 8);
				contents.newLineAtOffset(50, 110);
				contents.showText("Correspondence: test@test.edu");
				contents.endText();

				contents.beginText();
				contents.setFont(mainFont, 8);
				contents.newLineAtOffset(240, 30);
				contents.showText("Document generated for Spring AI Bug Report");
				contents.endText();
			}

			doc.save(path.toFile());
		}
	}

}
