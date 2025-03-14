package org.springframework.ai.chat.client.advisor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link NoOpUserTextProcessor}.
 *
 * @author Thomas Vitale
 */
class NoOpUserTextProcessorTests {

	@Test
	void process() {
		NoOpUserTextProcessor processor = new NoOpUserTextProcessor();
		String userText = "Hello, {World}!";
		String processedText = processor.process(userText, null);
		assertEquals(userText, processedText);
	}

}
