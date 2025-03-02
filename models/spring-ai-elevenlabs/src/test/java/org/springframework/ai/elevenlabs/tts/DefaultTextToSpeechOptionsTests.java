package org.springframework.ai.elevenlabs.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultTextToSpeechOptions}.
 *
 * @author Alexandros Pappas
 */
class DefaultTextToSpeechOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		TextToSpeechOptions options = DefaultTextToSpeechOptions.builder()
			.model("test-model")
			.voice("test-voice")
			.format("test-format")
			.speed(0.8)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getVoice()).isEqualTo("test-voice");
		assertThat(options.getFormat()).isEqualTo("test-format");
		assertThat(options.getSpeed()).isCloseTo(0.8, within(0.0001));
	}

	@Test
	void testCopy() {
		TextToSpeechOptions original = DefaultTextToSpeechOptions.builder()
			.model("test-model")
			.voice("test-voice")
			.format("test-format")
			.speed(0.8)
			.build();

		DefaultTextToSpeechOptions copied = original.copy();
		assertThat(copied).isNotSameAs(original).isEqualTo(original);
	}

	@Test
	void testDefaultValues() {
		DefaultTextToSpeechOptions options = DefaultTextToSpeechOptions.builder().build();
		assertThat(options.getModel()).isNull();
		assertThat(options.getVoice()).isNull();
		assertThat(options.getFormat()).isNull();
		assertThat(options.getSpeed()).isNull();
	}

}
