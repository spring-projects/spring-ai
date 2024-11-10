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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @author Tibor Tarnai
 */

class TextLineTest {

	public static Stream<Arguments> testWriteCharacterAtIndexValidIndex() {
		return Stream.of(Arguments.of(new Character('A', 0, false, false, false, false)),
				Arguments.of(new Character('A', 10, true, false, false, false)),
				Arguments.of(new Character('A', 0, false, true, false, false)));
	}

	@ParameterizedTest
	@MethodSource
	void testWriteCharacterAtIndexValidIndex(Character character) {
		TextLine textLine = new TextLine(100);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" A" + " ".repeat(23), textLine.getLine());
	}

	@Test
	void testWriteCharacterAtIndex_PartOfPreviousWord() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 10, true, false, false, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" A" + " ".repeat(23), textLine.getLine());
	}

	@Test
	void testWriteCharacterAtIndex_BeginningOfNewLine() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 0, false, true, false, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" A" + " ".repeat(23), textLine.getLine());
	}

	@Test
	void testWriteCharacterAtIndex_InvalidIndex() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 150, false, false, false, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" ".repeat(25), textLine.getLine());
	}

	@Test
	void testWriteCharacterAtIndex_NegativeIndex() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', -1, false, false, false, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" ".repeat(25), textLine.getLine());
	}

	@Test
	void testWriteCharacterAtIndex_SpaceCharacter() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 10, false, false, false, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" ".repeat(10) + "A" + " ".repeat(14), textLine.getLine());
	}

	@Test
	void testWriteCharacterAtIndex_CloseToPreviousWord() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 10, false, false, true, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" ".repeat(10) + "A" + " ".repeat(14), textLine.getLine());
	}

	@Test
	void testGetLineLength() {
		TextLine textLine = new TextLine(100);
		assertEquals(100 / ForkPDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT, textLine.getLineLength());
	}

	@Test
	void testGetLine() {
		TextLine textLine = new TextLine(100);
		assertEquals(" ".repeat(100 / ForkPDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT),
				textLine.getLine());
	}

	@Test
	void testNegativeLineLength() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new TextLine(-100));
		assertEquals("Line length cannot be negative", exception.getMessage());
	}

	@Test
	void testComputeIndexForCharacter_CloseToPreviousWord() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 10, true, false, true, true);
		textLine.writeCharacterAtIndex(character);
		assertEquals(" A" + " ".repeat(23), textLine.getLine());
	}

	@Test
	void testComputeIndexForCharacter_CloseToPreviousWord_WriteTwoCharacters() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 10, true, false, true, true);
		Character anotherCharacter = new Character('B', 1, true, false, true, true);
		textLine.writeCharacterAtIndex(character);
		textLine.writeCharacterAtIndex(anotherCharacter);
		assertEquals(" AB" + " ".repeat(22), textLine.getLine());
	}

}
