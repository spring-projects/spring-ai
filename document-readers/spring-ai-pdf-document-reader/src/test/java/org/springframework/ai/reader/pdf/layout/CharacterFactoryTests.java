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

import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Harshvardhan Singh
 */
class CharacterFactoryTests {

	private static final float PAGE_WIDTH = 612f;

	private static final float PAGE_HEIGHT = 792f;

	@Test
	void glyphWithoutUnicodeMappingIsReadAsSpace() {
		Character character = new CharacterFactory(true).createCharacterFromTextPosition(textPosition(40, ""),
				textPosition(20, "a"));

		assertThat(character.getCharacterValue()).isEqualTo(' ');
	}

	@Test
	void glyphWithoutUnicodeMappingLeavesNoNullCharacterInLine() {
		TextPosition previous = textPosition(20, "a");
		TextLine textLine = new TextLine(100);
		textLine.writeCharacterAtIndex(
				new CharacterFactory(true).createCharacterFromTextPosition(textPosition(40, ""), previous));
		textLine.writeCharacterAtIndex(
				new CharacterFactory(true).createCharacterFromTextPosition(textPosition(40, "b"), previous));

		assertThat(textLine.getLine()).doesNotContain("\u0000").contains("b");
	}

	/**
	 * Creates a text position for a single glyph on an unrotated page. pdfbox reports an
	 * empty unicode string for glyphs that have no unicode mapping.
	 */
	private static TextPosition textPosition(float x, String unicode) {
		Matrix textMatrix = Matrix.getTranslateInstance(x, PAGE_HEIGHT - 100);
		return new TextPosition(0, PAGE_WIDTH, PAGE_HEIGHT, textMatrix, x + 5f, PAGE_HEIGHT - 100, 10f, 5f, 3f, unicode,
				new int[] { 0 }, null, 10f, 10);
	}

}
