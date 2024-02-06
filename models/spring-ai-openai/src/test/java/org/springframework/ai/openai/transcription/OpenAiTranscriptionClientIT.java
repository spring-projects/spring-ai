package org.springframework.ai.openai.transcription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.OpenAiTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.transcription.TranscriptionOptions;
import org.springframework.ai.transcription.TranscriptionOptionsBuilder;
import org.springframework.ai.transcription.TranscriptionRequest;
import org.springframework.ai.transcription.TranscriptionResponse;
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
		TranscriptionOptions transcriptionOptions = TranscriptionOptionsBuilder.builder().withTemperature(0f).build();
		TranscriptionRequest transcriptionRequest = new TranscriptionRequest(audioFile, transcriptionOptions);
		TranscriptionResponse response = openAiTranscriptionClient.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

	@Test
	void transcriptionTestWithOptions() {
		OpenAiApi.TranscriptionRequest.ResponseFormat responseFormat = new OpenAiApi.TranscriptionRequest.ResponseFormat(
				"vtt");
		TranscriptionOptions transcriptionOptions = OpenAiTranscriptionOptions.builder()
			.withLanguage("en")
			.withPrompt("Ask not this, but ask that")
			.withTemperature(0f)
			.withResponseFormat(responseFormat)
			.build();
		TranscriptionRequest transcriptionRequest = new TranscriptionRequest(audioFile, transcriptionOptions);
		TranscriptionResponse response = openAiTranscriptionClient.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

}
