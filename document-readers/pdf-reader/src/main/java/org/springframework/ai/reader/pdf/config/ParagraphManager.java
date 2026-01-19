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

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * The ParagraphManager class is responsible for managing the paragraphs and hierarchy of
 * a PDF document. It can process bookmarks and generate a structured tree of paragraphs,
 * representing the table of contents (TOC) of the PDF document.
 *
 * @author Christian Tzolov
 */
public class ParagraphManager {

	/**
	 * Root of the paragraphs tree.
	 */
	private final Paragraph rootParagraph;

	private final PDDocument document;

	public ParagraphManager(PDDocument document) {

		Assert.notNull(document, "PDDocument must not be null");
		Assert.notNull(document.getDocumentCatalog().getDocumentOutline(),
				"Document outline (e.g. TOC) is null. "
						+ "Make sure the PDF document has a table of contents (TOC). If not, consider the "
						+ "PagePdfDocumentReader or the TikaDocumentReader instead.");

		try {

			this.document = document;

			this.rootParagraph = this.generateParagraphs(
					new Paragraph(null, "root", -1, 1, this.document.getNumberOfPages(), 0),
					this.document.getDocumentCatalog().getDocumentOutline(), 0);

			printParagraph(this.rootParagraph, System.out);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public List<Paragraph> flatten() {
		List<Paragraph> paragraphs = new ArrayList<>();
		for (var child : this.rootParagraph.children()) {
			flatten(child, paragraphs);
		}
		return paragraphs;
	}

	private void flatten(Paragraph current, List<Paragraph> paragraphs) {
		paragraphs.add(current);
		for (var child : current.children()) {
			flatten(child, paragraphs);
		}
	}

	private void printParagraph(Paragraph paragraph, PrintStream printStream) {
		printStream.println(paragraph);
		for (Paragraph childParagraph : paragraph.children()) {
			printParagraph(childParagraph, printStream);
		}
	}

	/**
	 * For given {@link PDOutlineNode} bookmark convert all sibling {@link PDOutlineItem}
	 * items into {@link Paragraph} instances under the parentParagraph. For each
	 * {@link PDOutlineItem} item, recursively call
	 * {@link ParagraphManager#generateParagraphs} to process its children items.
	 * @param parentParagraph Root paragraph that the bookmark sibling items should be
	 * added to.
	 * @param bookmark TOC paragraphs to process.
	 * @param level Current TOC deepness level.
	 * @return Returns a tree of {@link Paragraph}s that represent the PDF document TOC.
	 * @throws IOException
	 */
	protected Paragraph generateParagraphs(Paragraph parentParagraph, PDOutlineNode bookmark, Integer level)
			throws IOException {

		PDOutlineItem current = bookmark.getFirstChild();

		while (current != null) {

			int pageNumber = getPageNumber(current);
			var nextSiblingNumber = getPageNumber(current.getNextSibling());
			if (nextSiblingNumber < 0) {
				nextSiblingNumber = getPageNumber(current.getLastChild());
			}

			var paragraphPosition = (current.getDestination() instanceof PDPageXYZDestination)
					? ((PDPageXYZDestination) current.getDestination()).getTop() : 0;

			var currentParagraph = new Paragraph(parentParagraph, current.getTitle(), level, pageNumber,
					nextSiblingNumber, paragraphPosition);

			parentParagraph.children().add(currentParagraph);

			// Recursive call to go the current paragraph's children paragraphs.
			// E.g. go one level deeper.
			this.generateParagraphs(currentParagraph, current, level + 1);

			current = current.getNextSibling();
		}
		return parentParagraph;
	}

	private int getPageNumber(@Nullable PDOutlineItem current) throws IOException {
		if (current == null) {
			return -1;
		}
		PDPage currentPage = current.findDestinationPage(this.document);
		if (currentPage != null) {
			PDPageTree pages = this.document.getDocumentCatalog().getPages();
			for (int i = 0; i < pages.getCount(); i++) {
				var page = pages.get(i);
				if (page.equals(currentPage)) {
					return i + 1;
				}
			}
		}
		return -1;
	}

	public List<Paragraph> getParagraphsByLevel(Paragraph paragraph, int level, boolean interLevelText) {

		List<Paragraph> resultList = new ArrayList<>();

		if (paragraph.level() < level) {
			if (!CollectionUtils.isEmpty(paragraph.children())) {

				if (interLevelText) {
					var interLevelParagraph = new Paragraph(paragraph.parent(), paragraph.title(), paragraph.level(),
							paragraph.startPageNumber(), paragraph.children().get(0).startPageNumber(),
							paragraph.position());
					resultList.add(interLevelParagraph);
				}

				for (Paragraph child : paragraph.children()) {
					resultList.addAll(getParagraphsByLevel(child, level, interLevelText));
				}
			}
		}
		else if (paragraph.level() == level) {
			resultList.add(paragraph);
		}

		return resultList;
	}

	/**
	 * Represents a document paragraph metadata and hierarchy.
	 *
	 * @param parent Parent paragraph that will contain a children paragraphs.
	 * @param title Paragraph title as it appears in the PDF document.
	 * @param level The TOC deepness level for this paragraph. The root is at level 0.
	 * @param startPageNumber The page number in the PDF where this paragraph begins.
	 * @param endPageNumber The page number in the PDF where this paragraph ends.
	 * @param position The vertical position of the paragraph on the page.
	 * @param children Sub-paragraphs for this paragraph.
	 */
	public record Paragraph(@Nullable Paragraph parent, String title, int level, int startPageNumber, int endPageNumber,
			int position, List<Paragraph> children) {

		public Paragraph(@Nullable Paragraph parent, String title, int level, int startPageNumber, int endPageNumber,
				int position) {
			this(parent, title, level, startPageNumber, endPageNumber, position, new ArrayList<>());
		}

		@Override
		public String toString() {
			String indent = (this.level < 0) ? "" : new String(new char[this.level * 2]).replace('\0', ' ');

			return indent + " " + this.level + ") " + this.title + " [" + this.startPageNumber + ","
					+ this.endPageNumber + "], children = " + this.children.size() + ", pos = " + this.position;
		}

	}

}
