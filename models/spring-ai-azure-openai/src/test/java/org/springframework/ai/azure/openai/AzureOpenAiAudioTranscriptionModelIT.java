/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.core.credential.AzureKeyCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Piotr Olaszewski
 */
@SpringBootTest(classes = AzureOpenAiAudioTranscriptionModelIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_TRANSCRIPTION_DEPLOYMENT_NAME", matches = ".+")
class AzureOpenAiAudioTranscriptionModelIT {

	@Value("classpath:/speech/jfk.flac")
	private Resource audioFile;

	@Autowired
	private AzureOpenAiAudioTranscriptionModel transcriptionModel;

	@Test
	void transcriptionTest() {
		AzureOpenAiAudioTranscriptionOptions transcriptionOptions = AzureOpenAiAudioTranscriptionOptions.builder()
			.withResponseFormat(AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat.TEXT)
			.withTemperature(0f)
			.build();
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(this.audioFile,
				transcriptionOptions);
		AudioTranscriptionResponse response = this.transcriptionModel.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().toLowerCase().contains("fellow")).isTrue();
	}

	@Test
	void transcriptionTestWithOptions() {
		AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat responseFormat = AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat.VTT;

		AzureOpenAiAudioTranscriptionOptions transcriptionOptions = AzureOpenAiAudioTranscriptionOptions.builder()
			.withLanguage("en")
			.withPrompt("Ask not this, but ask that")
			.withTemperature(0f)
			.withResponseFormat(responseFormat)
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
		public OpenAIClient openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.serviceVersion(OpenAIServiceVersion.V2024_02_15_PREVIEW)
				.buildClient();
		}

		@Bean
		public AzureOpenAiAudioTranscriptionModel azureOpenAiChatModel(OpenAIClient openAIClient) {
			return new AzureOpenAiAudioTranscriptionModel(openAIClient,
					AzureOpenAiAudioTranscriptionOptions.builder()
						.withDeploymentName(System.getenv("AZURE_OPENAI_TRANSCRIPTION_DEPLOYMENT_NAME"))
						.build());
		}

	}

}
