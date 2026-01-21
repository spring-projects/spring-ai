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

package org.springframework.ai.reader.pdf.layout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.text.TextPositionComparator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends PDFTextStripper to provide custom text extraction and formatting
 * capabilities for PDF pages. It includes features like processing text lines, sorting
 * text positions, and managing line breaks.
 *
 * @author Jonathan Link
 *
 */
public class ForkPDFLayoutTextStripper extends PDFTextStripper {

	private final static Logger logger = LoggerFactory.getLogger(ForkPDFLayoutTextStripper.class);

	public static final boolean DEBUG = false;

	public static final int OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT = 4;

	private double currentPageWidth;

	private @Nullable TextPosition previousTextPosition;

	private List<TextLine> textLineList;

	/**
	 * Constructor
	 */
	public ForkPDFLayoutTextStripper() throws IOException {
		super();
		this.previousTextPosition = null;
		this.textLineList = new ArrayList<>();
	}

	/**
	 * @param page page to parse
	 */
	@Override
	public void processPage(PDPage page) throws IOException {
		PDRectangle pageRectangle = page.getMediaBox();
		if (pageRectangle != null) {
			this.setCurrentPageWidth(pageRectangle.getWidth() * 1.4);
			super.processPage(page);
			this.previousTextPosition = null;
			this.textLineList = new ArrayList<>();
		}
	}

	@Override
	protected void writePage() throws IOException {
		List<List<TextPosition>> charactersByArticle = super.getCharactersByArticle();
		for (List<TextPosition> textList : charactersByArticle) {
			try {
				this.sortTextPositionList(textList);
			}
			catch (IllegalArgumentException e) {
				logger.error("Error sorting text positions", e);
			}
			this.iterateThroughTextList(textList.iterator());
		}
		this.writeToOutputStream(this.getTextLineList());
	}

	private void writeToOutputStream(final List<TextLine> textLineList) throws IOException {
		for (TextLine textLine : textLineList) {
			char[] line = textLine.getLine().toCharArray();
			super.getOutput().write(line);
			super.getOutput().write('\n');
			super.getOutput().flush();
		}
	}

	/*
	 * In order to get rid of the warning: TextPositionComparator class should implement
	 * Comparator<TextPosition> instead of Comparator
	 */
	private void sortTextPositionList(final List<TextPosition> textList) {
		TextPositionComparator comparator = new TextPositionComparator();
		textList.sort(comparator);
	}

	private void writeLine(final List<TextPosition> textPositionList) {
		if (textPositionList.size() > 0) {
			TextLine textLine = this.addNewLine();
			boolean firstCharacterOfLineFound = false;
			for (TextPosition textPosition : textPositionList) {
				CharacterFactory characterFactory = new CharacterFactory(firstCharacterOfLineFound);
				Character character = characterFactory.createCharacterFromTextPosition(textPosition,
						this.getPreviousTextPosition());
				textLine.writeCharacterAtIndex(character);
				this.setPreviousTextPosition(textPosition);
				firstCharacterOfLineFound = true;
			}
		}
		else {
			this.addNewLine(); // white line
		}
	}

	private void iterateThroughTextList(Iterator<TextPosition> textIterator) {
		List<TextPosition> textPositionList = new ArrayList<>();

		while (textIterator.hasNext()) {
			TextPosition textPosition = (TextPosition) textIterator.next();
			int numberOfNewLines = this.getNumberOfNewLinesFromPreviousTextPosition(textPosition);
			if (numberOfNewLines == 0) {
				textPositionList.add(textPosition);
			}
			else {
				this.writeTextPositionList(textPositionList);
				this.createNewEmptyNewLines(numberOfNewLines);
				textPositionList.add(textPosition);
			}
			this.setPreviousTextPosition(textPosition);
		}
		if (!textPositionList.isEmpty()) {
			this.writeTextPositionList(textPositionList);
		}
	}

	private void writeTextPositionList(final List<TextPosition> textPositionList) {
		this.writeLine(textPositionList);
		textPositionList.clear();
	}

	private void createNewEmptyNewLines(int numberOfNewLines) {
		for (int i = 0; i < numberOfNewLines - 1; ++i) {
			this.addNewLine();
		}
	}

	private int getNumberOfNewLinesFromPreviousTextPosition(final TextPosition textPosition) {
		TextPosition previousTextPosition = this.getPreviousTextPosition();
		if (previousTextPosition == null) {
			return 1;
		}

		float textYPosition = Math.round(textPosition.getY());
		float previousTextYPosition = Math.round(previousTextPosition.getY());

		if (textYPosition > previousTextYPosition && (textYPosition - previousTextYPosition > 5.5)) {
			double height = textPosition.getHeight();
			int numberOfLines = (int) (Math.floor(textYPosition - previousTextYPosition) / height);
			numberOfLines = Math.max(1, numberOfLines - 1); // exclude current new line
			if (DEBUG) {
				System.out.println(height + " " + numberOfLines);
			}
			return numberOfLines;
		}
		else {
			return 0;
		}
	}

	private TextLine addNewLine() {
		TextLine textLine = new TextLine(this.getCurrentPageWidth());
		this.textLineList.add(textLine);
		return textLine;
	}

	private @Nullable TextPosition getPreviousTextPosition() {
		return this.previousTextPosition;
	}

	private void setPreviousTextPosition(final TextPosition setPreviousTextPosition) {
		this.previousTextPosition = setPreviousTextPosition;
	}

	private int getCurrentPageWidth() {
		return (int) Math.round(this.currentPageWidth);
	}

	private void setCurrentPageWidth(double currentPageWidth) {
		this.currentPageWidth = currentPageWidth;
	}

	private List<TextLine> getTextLineList() {
		return this.textLineList;
	}

}
