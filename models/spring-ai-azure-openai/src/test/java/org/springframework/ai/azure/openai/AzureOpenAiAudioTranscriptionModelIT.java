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

import java.util.concurrent.TimeUnit;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.okhttp.OkHttpAsyncHttpClientBuilder;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

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
 * NOTE - Use deployment name "whisper"
 *
 * @author Piotr Olaszewski
 */
@SpringBootTest
@EnabledIfEnvironmentVariables({
		@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_TRANSCRIPTION_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_TRANSCRIPTION_ENDPOINT", matches = ".+") })
class AzureOpenAiAudioTranscriptionModelIT {

	@Value("classpath:/speech/jfk.flac")
	private Resource audioFile;

	@Autowired
	private AzureOpenAiAudioTranscriptionModel transcriptionModel;

	@Test
	void transcriptionTest() {
		AzureOpenAiAudioTranscriptionOptions transcriptionOptions = AzureOpenAiAudioTranscriptionOptions.builder()
			.responseFormat(AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat.TEXT)
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
		AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat responseFormat = AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat.VTT;

		AzureOpenAiAudioTranscriptionOptions transcriptionOptions = AzureOpenAiAudioTranscriptionOptions.builder()
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
		public OpenAIClient openAIClient() {
			String apiKey = System.getenv("AZURE_OPENAI_TRANSCRIPTION_API_KEY");
			String endpoint = System.getenv("AZURE_OPENAI_TRANSCRIPTION_ENDPOINT");

			// System.out.println("API Key: " + apiKey);
			// System.out.println("Endpoint: " + endpoint);
			int readTimeout = 120;
			int writeTimeout = 120;

			// OkHttp client with long timeouts
			OkHttpClient okHttpClient = new OkHttpClient.Builder().readTimeout(readTimeout, TimeUnit.SECONDS)
				.callTimeout(writeTimeout, TimeUnit.SECONDS)
				.build();

			return new OpenAIClientBuilder().httpClient(new OkHttpAsyncHttpClientBuilder(okHttpClient).build())
				.credential(new AzureKeyCredential(apiKey))
				.endpoint(endpoint)
				// .serviceVersion(OpenAIServiceVersion.V2024_02_15_PREVIEW)
				.buildClient();
		}

		@Bean
		public AzureOpenAiAudioTranscriptionModel azureOpenAiChatModel(OpenAIClient openAIClient) {
			return new AzureOpenAiAudioTranscriptionModel(openAIClient,
					AzureOpenAiAudioTranscriptionOptions.builder().deploymentName("whisper").build());
		}

	}

}
