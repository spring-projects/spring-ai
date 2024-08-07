/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai.audio.transcription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
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
class OpenAiTranscriptionModelIT extends AbstractIT {

	@Value("classpath:/speech/jfk.flac")
	private Resource audioFile;

	@Test
	void transcriptionTest() {
		OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
			.withResponseFormat(TranscriptResponseFormat.TEXT)
			.withTemperature(0f)
			.build();
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
		AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);
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
		AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

}
