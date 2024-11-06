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

import org.apache.pdfbox.text.TextPosition;

class CharacterFactory {

	private TextPosition previousTextPosition;

	private boolean firstCharacterOfLineFound;

	private boolean isCharacterPartOfPreviousWord;

	private boolean isFirstCharacterOfAWord;

	private boolean isCharacterAtTheBeginningOfNewLine;

	private boolean isCharacterCloseToPreviousWord;

	CharacterFactory(boolean firstCharacterOfLineFound) {
		this.firstCharacterOfLineFound = firstCharacterOfLineFound;
	}

	public Character createCharacterFromTextPosition(final TextPosition textPosition,
			final TextPosition previousTextPosition) {
		this.setPreviousTextPosition(previousTextPosition);
		this.isCharacterPartOfPreviousWord = this.isCharacterPartOfPreviousWord(textPosition);
		this.isFirstCharacterOfAWord = this.isFirstCharacterOfAWord(textPosition);
		this.isCharacterAtTheBeginningOfNewLine = this.isCharacterAtTheBeginningOfNewLine(textPosition);
		this.isCharacterCloseToPreviousWord = this.isCharacterCloseToPreviousWord(textPosition);
		char character = this.getCharacterFromTextPosition(textPosition);
		int index = (int) textPosition.getX() / ForkPDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
		return new Character(character, index, this.isCharacterPartOfPreviousWord, this.isFirstCharacterOfAWord,
				this.isCharacterAtTheBeginningOfNewLine, this.isCharacterCloseToPreviousWord);
	}

	private boolean isCharacterAtTheBeginningOfNewLine(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return true;
		}
		TextPosition previousTextPosition = this.getPreviousTextPosition();
		float previousTextYPosition = previousTextPosition.getY();
		return (Math.round(textPosition.getY()) < Math.round(previousTextYPosition));
	}

	private boolean isFirstCharacterOfAWord(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return true;
		}
		double numberOfSpaces = this.numberOfSpacesBetweenTwoCharacters(this.previousTextPosition, textPosition);
		return (numberOfSpaces > 1) || this.isCharacterAtTheBeginningOfNewLine(textPosition);
	}

	private boolean isCharacterCloseToPreviousWord(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return false;
		}
		double numberOfSpaces = this.numberOfSpacesBetweenTwoCharacters(this.previousTextPosition, textPosition);
		return (numberOfSpaces > 1 && numberOfSpaces <= ForkPDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT);
	}

	private boolean isCharacterPartOfPreviousWord(final TextPosition textPosition) {
		TextPosition previousTextPosition = this.getPreviousTextPosition();
		if (previousTextPosition.getUnicode().equals(" ")) {
			return false;
		}
		double numberOfSpaces = this.numberOfSpacesBetweenTwoCharacters(previousTextPosition, textPosition);
		return (numberOfSpaces <= 1);
	}

	private double numberOfSpacesBetweenTwoCharacters(final TextPosition textPosition1,
			final TextPosition textPosition2) {
		double previousTextXPosition = textPosition1.getX();
		double previousTextWidth = textPosition1.getWidth();
		double previousTextEndXPosition = (previousTextXPosition + previousTextWidth);
		double numberOfSpaces = Math.abs(Math.round(textPosition2.getX() - previousTextEndXPosition));
		return numberOfSpaces;
	}

	private char getCharacterFromTextPosition(final TextPosition textPosition) {
		String string = textPosition.getUnicode();
		char character = string.charAt(0);
		return character;
	}

	private TextPosition getPreviousTextPosition() {
		return this.previousTextPosition;
	}

	private void setPreviousTextPosition(final TextPosition previousTextPosition) {
		this.previousTextPosition = previousTextPosition;
	}

}
