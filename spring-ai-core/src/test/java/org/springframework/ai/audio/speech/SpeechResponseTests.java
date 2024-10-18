package org.springframework.ai.audio.speech;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SpeechResponse}.
 *
 * @author Thomas Vitale
 */
class SpeechResponseTests {

	@Test
	void whenBuildSpeechResponse() {
		var speech = new Speech(new byte[0]);
		var speechResponse = new SpeechResponse(speech);
		assertThat(speechResponse.getResult()).isEqualTo(speech);
		assertThat(speechResponse.getResults()).isEqualTo(List.of(speech));
		assertThat(speechResponse.getMetadata()).isNotNull();
	}

	@Test
	void whenSpeechIsNullThenThrow() {
		assertThatThrownBy(() -> new SpeechResponse(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("speech cannot be null");
	}

	@Test
	void whenMetadataIsNullThenThrow() {
		assertThatThrownBy(() -> new SpeechResponse(new Speech(new byte[0]), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("speechResponseMetadata cannot be null");
	}

}
