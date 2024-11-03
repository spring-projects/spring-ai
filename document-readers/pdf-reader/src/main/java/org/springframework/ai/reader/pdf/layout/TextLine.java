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

class TextLine {

	private static final char SPACE_CHARACTER = ' ';

	private int lineLength;

	private String line;

	private int lastIndex;

	TextLine(int lineLength) {
		this.line = "";
		this.lineLength = lineLength / ForkPDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
		this.completeLineWithSpaces();
	}

	public void writeCharacterAtIndex(final Character character) {
		character.setIndex(this.computeIndexForCharacter(character));
		int index = character.getIndex();
		char characterValue = character.getCharacterValue();
		if (this.indexIsInBounds(index) && this.line.charAt(index) == SPACE_CHARACTER) {
			this.line = this.line.substring(0, index) + characterValue
					+ this.line.substring(index + 1, this.getLineLength());
		}
	}

	public int getLineLength() {
		return this.lineLength;
	}

	public String getLine() {
		return this.line;
	}

	private int computeIndexForCharacter(final Character character) {
		int index = character.getIndex();
		boolean isCharacterPartOfPreviousWord = character.isCharacterPartOfPreviousWord();
		boolean isCharacterAtTheBeginningOfNewLine = character.isCharacterAtTheBeginningOfNewLine();
		boolean isCharacterCloseToPreviousWord = character.isCharacterCloseToPreviousWord();

		if (!this.indexIsInBounds(index)) {
			return -1;
		}
		else {
			if (isCharacterPartOfPreviousWord && !isCharacterAtTheBeginningOfNewLine) {
				index = this.findMinimumIndexWithSpaceCharacterFromIndex(index);
			}
			else if (isCharacterCloseToPreviousWord) {
				if (this.line.charAt(index) != SPACE_CHARACTER) {
					index = index + 1;
				}
				else {
					index = this.findMinimumIndexWithSpaceCharacterFromIndex(index) + 1;
				}
			}
			index = this.getNextValidIndex(index, isCharacterPartOfPreviousWord);
			return index;
		}
	}

	private boolean isSpaceCharacterAtIndex(int index) {
		return this.line.charAt(index) != SPACE_CHARACTER;
	}

	private boolean isNewIndexGreaterThanLastIndex(int index) {
		int lastIndex = this.getLastIndex();
		return (index > lastIndex);
	}

	private int getNextValidIndex(int index, boolean isCharacterPartOfPreviousWord) {
		int nextValidIndex = index;
		int lastIndex = this.getLastIndex();
		if (!this.isNewIndexGreaterThanLastIndex(index)) {
			nextValidIndex = lastIndex + 1;
		}
		if (!isCharacterPartOfPreviousWord && this.isSpaceCharacterAtIndex(index - 1)) {
			nextValidIndex = nextValidIndex + 1;
		}
		this.setLastIndex(nextValidIndex);
		return nextValidIndex;
	}

	private int findMinimumIndexWithSpaceCharacterFromIndex(int index) {
		int newIndex = index;
		while (newIndex >= 0 && this.line.charAt(newIndex) == SPACE_CHARACTER) {
			newIndex = newIndex - 1;
		}
		return newIndex + 1;
	}

	private boolean indexIsInBounds(int index) {
		return (index >= 0 && index < this.lineLength);
	}

	private void completeLineWithSpaces() {
		for (int i = 0; i < this.getLineLength(); ++i) {
			this.line += SPACE_CHARACTER;
		}
	}

	private int getLastIndex() {
		return this.lastIndex;
	}

	private void setLastIndex(int lastIndex) {
		this.lastIndex = lastIndex;
	}

}
