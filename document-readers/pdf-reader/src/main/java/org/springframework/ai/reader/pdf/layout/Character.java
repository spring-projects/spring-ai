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

class Character {

	private final char characterValue;

	private int index;

	private final boolean isCharacterPartOfPreviousWord;

	private final boolean isFirstCharacterOfAWord;

	private final boolean isCharacterAtTheBeginningOfNewLine;

	private final boolean isCharacterCloseToPreviousWord;

	Character(char characterValue, int index, boolean isCharacterPartOfPreviousWord, boolean isFirstCharacterOfAWord,
			boolean isCharacterAtTheBeginningOfNewLine, boolean isCharacterPartOfASentence) {
		this.characterValue = characterValue;
		this.index = index;
		this.isCharacterPartOfPreviousWord = isCharacterPartOfPreviousWord;
		this.isFirstCharacterOfAWord = isFirstCharacterOfAWord;
		this.isCharacterAtTheBeginningOfNewLine = isCharacterAtTheBeginningOfNewLine;
		this.isCharacterCloseToPreviousWord = isCharacterPartOfASentence;
		if (ForkPDFLayoutTextStripper.DEBUG) {
			System.out.println(this.toString());
		}
	}

	public char getCharacterValue() {
		return this.characterValue;
	}

	public int getIndex() {
		return this.index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public boolean isCharacterPartOfPreviousWord() {
		return this.isCharacterPartOfPreviousWord;
	}

	public boolean isFirstCharacterOfAWord() {
		return this.isFirstCharacterOfAWord;
	}

	public boolean isCharacterAtTheBeginningOfNewLine() {
		return this.isCharacterAtTheBeginningOfNewLine;
	}

	public boolean isCharacterCloseToPreviousWord() {
		return this.isCharacterCloseToPreviousWord;
	}

	public String toString() {
		String toString = "";
		toString += this.index;
		toString += " ";
		toString += this.characterValue;
		toString += " isCharacterPartOfPreviousWord=" + this.isCharacterPartOfPreviousWord;
		toString += " isFirstCharacterOfAWord=" + this.isFirstCharacterOfAWord;
		toString += " isCharacterAtTheBeginningOfNewLine=" + this.isCharacterAtTheBeginningOfNewLine;
		toString += " isCharacterPartOfASentence=" + this.isCharacterCloseToPreviousWord;
		toString += " isCharacterCloseToPreviousWord=" + this.isCharacterCloseToPreviousWord;
		return toString;
	}

}
