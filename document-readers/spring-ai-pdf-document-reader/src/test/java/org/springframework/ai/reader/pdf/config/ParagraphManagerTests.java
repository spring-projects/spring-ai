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

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import org.springframework.ai.reader.pdf.config.ParagraphManager.Paragraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests for {@link ParagraphManager}.
 */
class ParagraphManagerTests {

	private static final int PAGE_COUNT = 8;

	/**
	 * A paragraph ends where the next item in reading order begins. For an item with no
	 * following sibling that is the next item after its parent, and for the last item of
	 * the document it is the last page.
	 */
	@Test
	void endPageIsWhereTheNextItemInReadingOrderBegins() throws IOException {

		try (PDDocument document = outlinedDocument()) {

			var paragraphs = new ParagraphManager(document).flatten();

			assertThat(paragraphs).extracting(Paragraph::title, Paragraph::endPageNumber)
				.containsExactly(tuple("Chapter 1", 4), tuple("Section 1.1", 3), tuple("Section 1.2", 4),
						tuple("Chapter 2", PAGE_COUNT), tuple("Section 2.1", 6), tuple("Section 2.2", PAGE_COUNT));
		}
	}

	/**
	 * Builds an eight page document outlined as two chapters of two sections each, so
	 * that the last section of each chapter has neither a following sibling nor a child,
	 * and the last chapter has no following sibling.
	 */
	private static PDDocument outlinedDocument() {

		PDDocument document = new PDDocument();
		for (int i = 0; i < PAGE_COUNT; i++) {
			document.addPage(new PDPage());
		}

		PDDocumentOutline outline = new PDDocumentOutline();
		document.getDocumentCatalog().setDocumentOutline(outline);

		PDOutlineItem chapterOne = bookmark(document, "Chapter 1", 1);
		chapterOne.addLast(bookmark(document, "Section 1.1", 2));
		chapterOne.addLast(bookmark(document, "Section 1.2", 3));
		outline.addLast(chapterOne);

		PDOutlineItem chapterTwo = bookmark(document, "Chapter 2", 4);
		chapterTwo.addLast(bookmark(document, "Section 2.1", 5));
		chapterTwo.addLast(bookmark(document, "Section 2.2", 6));
		outline.addLast(chapterTwo);

		return document;
	}

	private static PDOutlineItem bookmark(PDDocument document, String title, int pageNumber) {

		PDPageXYZDestination destination = new PDPageXYZDestination();
		destination.setPage(document.getPage(pageNumber - 1));

		PDOutlineItem bookmark = new PDOutlineItem();
		bookmark.setTitle(title);
		bookmark.setDestination(destination);
		return bookmark;
	}

}
