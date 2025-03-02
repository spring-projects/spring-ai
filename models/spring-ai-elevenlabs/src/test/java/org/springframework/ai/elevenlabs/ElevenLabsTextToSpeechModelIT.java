package org.springframework.ai.elevenlabs;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.elevenlabs.tts.Speech;
import org.springframework.ai.elevenlabs.tts.TextToSpeechPrompt;
import org.springframework.ai.elevenlabs.tts.TextToSpeechResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for the {@link ElevenLabsTextToSpeechModel}.
 *
 * <p>
 * These tests require a valid ElevenLabs API key to be set as an environment variable
 * named {@code ELEVEN_LABS_API_KEY}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = ElevenLabsTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ELEVEN_LABS_API_KEY", matches = ".+")
public class ElevenLabsTextToSpeechModelIT {

	private static final String VOICE_ID = "9BWtsMINqrJLrRacOk9x";

	@Autowired
	private ElevenLabsTextToSpeechModel textToSpeechModel;

	@Test
	void textToSpeechWithVoiceTest() {
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder().voice(VOICE_ID).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello, world!", options);
		TextToSpeechResponse response = textToSpeechModel.call(prompt);

		assertThat(response).isNotNull();
		List<Speech> results = response.getResults();
		assertThat(results).hasSize(1);
		Speech speech = results.get(0);
		assertThat(speech.getOutput()).isNotEmpty();
	}

	@Test
	void textToSpeechStreamWithVoiceTest() {
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder().voice(VOICE_ID).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(
				"Hello, world! This is a test of streaming speech synthesis.", options);
		Flux<TextToSpeechResponse> responseFlux = textToSpeechModel.stream(prompt);

		List<TextToSpeechResponse> responses = responseFlux.collectList().block();
		assertThat(responses).isNotNull().isNotEmpty();

		responses.forEach(response -> {
			assertThat(response).isNotNull();
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		});
	}

}
