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

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.text.TextPositionComparator;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author zxuhan7
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

}
