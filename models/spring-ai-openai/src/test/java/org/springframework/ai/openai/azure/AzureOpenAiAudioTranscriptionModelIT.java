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

package org.springframework.ai.openai.azure;

import com.openai.models.audio.AudioResponseFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTE - Use deployment name "whisper"
 *
 * @author Piotr Olaszewski
 */
@SpringBootTest(classes = AzureOpenAiAudioTranscriptionModelIT.TestConfiguration.class)
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+") })
class AzureOpenAiAudioTranscriptionModelIT {

	@Value("classpath:/speech.flac")
	private Resource audioFile;

	@Autowired
	private OpenAiAudioTranscriptionModel transcriptionModel;

	@Test
	void transcriptionTest() {
		OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
			.responseFormat(AudioResponseFormat.TEXT)
			.temperature(0f)
			.build();
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(this.audioFile,
				transcriptionOptions);
		AudioTranscriptionResponse response = this.transcriptionModel.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

	@Test
	void transcriptionTestWithOptions() {
		AudioResponseFormat responseFormat = AudioResponseFormat.VTT;

		OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.prompt("Ask not this, but ask that")
			.temperature(0f)
			.responseFormat(responseFormat)
			.build();
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(this.audioFile,
				transcriptionOptions);
		AudioTranscriptionResponse response = this.transcriptionModel.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAiAudioTranscriptionModel azureOpenAiChatModel() {
			return OpenAiAudioTranscriptionModel.builder()
				.options(OpenAiAudioTranscriptionOptions.builder()
					.baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
					.apiKey(System.getenv("AZURE_OPENAI_API_KEY"))
					.deploymentName("whisper")
					.build())
				.build();
		}

	}

}
