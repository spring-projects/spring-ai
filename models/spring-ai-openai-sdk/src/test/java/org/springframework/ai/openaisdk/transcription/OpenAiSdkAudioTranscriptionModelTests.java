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

package org.springframework.ai.openaisdk.transcription;

import com.openai.client.OpenAIClient;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.services.blocking.AudioService;
import com.openai.services.blocking.audio.TranscriptionService;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openaisdk.OpenAiSdkAudioTranscriptionModel;
import org.springframework.ai.openaisdk.OpenAiSdkAudioTranscriptionOptions;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiSdkAudioTranscriptionModel} and
 * {@link OpenAiSdkAudioTranscriptionOptions}.
 *
 * @author Ilayaperumal Gopinathan
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class OpenAiSdkAudioTranscriptionModelTests {

	private OpenAIClient createMockClient(TranscriptionCreateResponse mockResponse) {
		OpenAIClient client = mock(OpenAIClient.class);
		AudioService audioService = mock(AudioService.class);
		TranscriptionService transcriptionService = mock(TranscriptionService.class);
		when(client.audio()).thenReturn(audioService);
		when(audioService.transcriptions()).thenReturn(transcriptionService);
		when(transcriptionService.create(any())).thenReturn(mockResponse);
		return client;
	}

	@Test
	void callReturnsTranscriptionText() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Hello, transcribed text").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionModel model = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.build();
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = model.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, transcribed text");
	}

	@Test
	void callWithDefaultOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Hello, this is a test transcription.").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionModel model = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = model.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, this is a test transcription.");
		assertThat(response.getResults()).hasSize(1);
	}

	@Test
	void callWithPromptOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Hello, this is a test transcription with options.").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionOptions options = OpenAiSdkAudioTranscriptionOptions.builder()
			.temperature(0.5f)
			.responseFormat(AudioResponseFormat.JSON)
			.build();

		OpenAiSdkAudioTranscriptionModel model = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = model.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, this is a test transcription with options.");
	}

	@Test
	void transcribeWithResourceReturnsText() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Simple output").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionModel model = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.build();
		String text = model.transcribe(new ClassPathResource("/speech.flac"));

		assertThat(text).isEqualTo("Simple output");
	}

	@Test
	void transcribeWithOptionsUsesMergedOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("With options").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionOptions options = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();
		OpenAiSdkAudioTranscriptionModel model = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.options(options)
			.build();
		String text = model.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(text).isEqualTo("With options");
	}

	@Test
	void optionsBuilderFromCopiesAllFields() {
		OpenAiSdkAudioTranscriptionOptions original = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.responseFormat(AudioResponseFormat.VERBOSE_JSON)
			.language("en")
			.prompt("test prompt")
			.temperature(0.5f)
			.baseUrl("https://custom.api.com")
			.apiKey("test-key")
			.organizationId("org-123")
			.build();

		OpenAiSdkAudioTranscriptionOptions copied = OpenAiSdkAudioTranscriptionOptions.builder().from(original).build();

		assertThat(copied.getModel()).isEqualTo("whisper-1");
		assertThat(copied.getResponseFormat()).isEqualTo(AudioResponseFormat.VERBOSE_JSON);
		assertThat(copied.getLanguage()).isEqualTo("en");
		assertThat(copied.getPrompt()).isEqualTo("test prompt");
		assertThat(copied.getTemperature()).isEqualTo(0.5f);
		assertThat(copied.getBaseUrl()).isEqualTo("https://custom.api.com");
		assertThat(copied.getApiKey()).isEqualTo("test-key");
		assertThat(copied.getOrganizationId()).isEqualTo("org-123");
	}

	@Test
	void optionsBuilderMergeOverridesNonNullValues() {
		OpenAiSdkAudioTranscriptionOptions base = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.temperature(0.5f)
			.build();

		OpenAiSdkAudioTranscriptionOptions override = OpenAiSdkAudioTranscriptionOptions.builder()
			.language("de")
			.prompt("new prompt")
			.build();

		OpenAiSdkAudioTranscriptionOptions merged = OpenAiSdkAudioTranscriptionOptions.builder()
			.from(base)
			.merge(override)
			.build();

		assertThat(merged.getModel()).isEqualTo("whisper-1");
		assertThat(merged.getLanguage()).isEqualTo("de");
		assertThat(merged.getPrompt()).isEqualTo("new prompt");
		assertThat(merged.getTemperature()).isEqualTo(0.5f);
	}

	@Test
	void optionsCopyCreatesIndependentInstance() {
		OpenAiSdkAudioTranscriptionOptions original = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();

		OpenAiSdkAudioTranscriptionOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getLanguage()).isEqualTo(original.getLanguage());
	}

	@Test
	void optionsEqualsAndHashCode() {
		OpenAiSdkAudioTranscriptionOptions options1 = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.temperature(0.5f)
			.build();

		OpenAiSdkAudioTranscriptionOptions options2 = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.temperature(0.5f)
			.build();

		OpenAiSdkAudioTranscriptionOptions options3 = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("de")
			.temperature(0.5f)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	void optionsToStringContainsFields() {
		OpenAiSdkAudioTranscriptionOptions options = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();

		String str = options.toString();
		assertThat(str).contains("whisper-1");
		assertThat(str).contains("en");
	}

	@Test
	void optionsBuilderWithAzureConfiguration() {
		OpenAiSdkAudioTranscriptionOptions options = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.deploymentName("my-deployment")
			.microsoftFoundry(true)
			.baseUrl("https://my-resource.openai.azure.com")
			.build();

		assertThat(options.getDeploymentName()).isEqualTo("my-deployment");
		assertThat(options.isMicrosoftFoundry()).isTrue();
		assertThat(options.getBaseUrl()).isEqualTo("https://my-resource.openai.azure.com");
	}

	@Test
	void mutateCreatesBuilderWithSameConfiguration() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Mutated model output").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionOptions options = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();

		OpenAiSdkAudioTranscriptionModel originalModel = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.options(options)
			.build();

		OpenAiSdkAudioTranscriptionModel mutatedModel = originalModel.mutate().build();

		assertThat(mutatedModel.getOptions().getModel()).isEqualTo("whisper-1");
		assertThat(mutatedModel.getOptions().getLanguage()).isEqualTo("en");

		String text = mutatedModel.transcribe(new ClassPathResource("/speech.flac"));
		assertThat(text).isEqualTo("Mutated model output");
	}

	@Test
	void mutateAllowsOverridingOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Modified options output").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiSdkAudioTranscriptionOptions originalOptions = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();

		OpenAiSdkAudioTranscriptionModel originalModel = OpenAiSdkAudioTranscriptionModel.builder()
			.openAiClient(client)
			.options(originalOptions)
			.build();

		OpenAiSdkAudioTranscriptionOptions newOptions = OpenAiSdkAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("de")
			.temperature(0.5f)
			.build();

		OpenAiSdkAudioTranscriptionModel mutatedModel = originalModel.mutate().options(newOptions).build();

		assertThat(mutatedModel.getOptions().getLanguage()).isEqualTo("de");
		assertThat(mutatedModel.getOptions().getTemperature()).isEqualTo(0.5f);
	}

}
