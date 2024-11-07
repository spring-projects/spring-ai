package org.springframework.ai.reader.pdf.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class TextLineTest {

	@Test
	void testWriteCharacterAtIndex_BeginningOfNewLine() {
		TextLine textLine = new TextLine(100);
		Character character = new Character('A', 0, false, true, false, false);
		textLine.writeCharacterAtIndex(character);
		assertEquals("A" + " ".repeat(99), textLine.getLine());
	}
}
