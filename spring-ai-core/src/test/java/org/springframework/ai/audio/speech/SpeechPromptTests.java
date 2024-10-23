package org.springframework.ai.audio.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SpeechPrompt}.
 *
 * @author Thomas Vitale
 */
class SpeechPromptTests {

	@Test
	void whenMessageIsNullThenThrow() {
		assertThatThrownBy(() -> new SpeechPrompt(null, SpeechOptionsBuilder.builder().build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("message cannot be null");
	}

	@Test
	void whenOptionsIsNullThenThrow() {
		assertThatThrownBy(() -> new SpeechPrompt(new SpeechMessage("hobbits"), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("speechOptions cannot be null");
	}

	@Test
	void whenMessageIsNullInBuilderThenThrow() {
		assertThatThrownBy(() -> SpeechPrompt.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("message cannot be null");
	}

}