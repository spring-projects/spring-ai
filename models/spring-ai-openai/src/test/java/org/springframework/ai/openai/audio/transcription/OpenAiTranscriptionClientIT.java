package org.springframework.ai.openai.audio.transcription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiTranscriptionClientIT extends AbstractIT {

	@Value("classpath:/speech/jfk.flac")
	private Resource audioFile;

	@Test
	void transcriptionTest() {
		OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
			.withResponseFormat(TranscriptResponseFormat.TEXT)
			.withTemperature(0f)
			.build();
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
		AudioTranscriptionResponse response = openAiTranscriptionClient.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

	@Test
	void transcriptionTestWithOptions() {
		OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.VTT;

		OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
			.withLanguage("en")
			.withPrompt("Ask not this, but ask that")
			.withTemperature(0f)
			.withResponseFormat(responseFormat)
			.build();
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
		AudioTranscriptionResponse response = openAiTranscriptionClient.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

}
