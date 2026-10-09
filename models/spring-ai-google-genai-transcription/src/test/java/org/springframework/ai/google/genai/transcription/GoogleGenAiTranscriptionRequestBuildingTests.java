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

package org.springframework.ai.google.genai.transcription;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests verifying the {@code generateContent} request built by
 * {@link GoogleGenAiTranscriptionModel} and the mapping of the response.
 *
 * @author Olivier Le Quellec
 */
@ExtendWith(MockitoExtension.class)
class GoogleGenAiTranscriptionRequestBuildingTests {

	private Client mockGenAiClient;

	@Mock
	private Models mockModels;

	private GoogleGenAiTranscriptionModel transcriptionModel;

	@BeforeEach
	void setUp() throws Exception {
		// Client and Models are final classes; 'models' is a public final field, so set
		// it via reflection on the mock.
		this.mockGenAiClient = mock(Client.class);
		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(this.mockGenAiClient, this.mockModels);

		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.client(this.mockGenAiClient)
			.build();

		this.transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				GoogleGenAiAudioTranscriptionOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	@Test
	void defaultModelAndPromptAreUsedWhenNotSet() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("hello world", "gemini-3.5-transcribe", 10, 5, 15));

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(audioResource("sample.wav")));

		assertThat(response.getResult().getOutput()).isEqualTo("hello world");

		ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
		verify(this.mockModels).generateContent(modelCaptor.capture(), contentCaptor.capture(),
				nullable(GenerateContentConfig.class));

		assertThat(modelCaptor.getValue()).isEqualTo(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME);

		Content content = contentCaptor.getValue();
		assertThat(content.role()).contains("user");
		assertThat(content.parts()).isPresent();
		assertThat(content.parts().get()).hasSize(2);
		assertThat(content.parts().get().get(1).text())
			.contains(GoogleGenAiAudioTranscriptionOptions.DEFAULT_TRANSCRIPTION_PROMPT);
	}

	@Test
	void mimeTypeIsDetectedFromFilenameExtension() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("speech.mp3")));

		ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
		verify(this.mockModels).generateContent(anyString(), contentCaptor.capture(),
				nullable(GenerateContentConfig.class));

		Part audioPart = contentCaptor.getValue().parts().get().get(0);
		assertThat(audioPart.inlineData()).isPresent();
		assertThat(audioPart.inlineData().get().mimeType()).contains("audio/mp3");
	}

	@Test
	void missingExtensionFallsBackToAudioWav() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("speech")));

		ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
		verify(this.mockModels).generateContent(anyString(), contentCaptor.capture(),
				nullable(GenerateContentConfig.class));

		assertThat(contentCaptor.getValue().parts().get().get(0).inlineData().get().mimeType()).contains("audio/wav");
	}

	@Test
	void customPromptAndLanguageAreIncludedInInstruction() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.prompt("Transcribe this podcast.")
			.language("fr-FR")
			.build();

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("sample.wav"), options));

		ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
		verify(this.mockModels).generateContent(anyString(), contentCaptor.capture(),
				nullable(GenerateContentConfig.class));

		assertThat(contentCaptor.getValue().parts().get().get(1).text())
			.contains("Transcribe this podcast. The audio language is fr-FR.");
	}

	@Test
	void temperatureIsPassedInGenerateContentConfig() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), any(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.temperature(0.3f)
			.build();

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("sample.wav"), options));

		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.mockModels).generateContent(anyString(), any(Content.class), configCaptor.capture());

		assertThat(configCaptor.getValue().temperature()).contains(0.3f);
	}

	@Test
	void generationOptionsArePassedInGenerateContentConfig() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), any(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.topP(0.9f)
			.topK(40f)
			.candidateCount(1)
			.maxOutputTokens(2048)
			.stopSequences(java.util.List.of("\n\n"))
			.responseMimeType("text/plain")
			.presencePenalty(0.1f)
			.frequencyPenalty(0.2f)
			.seed(42)
			.build();

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("sample.wav"), options));

		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.mockModels).generateContent(anyString(), any(Content.class), configCaptor.capture());

		GenerateContentConfig config = configCaptor.getValue();
		assertThat(config.topP()).contains(0.9f);
		assertThat(config.topK()).contains(40f);
		assertThat(config.candidateCount()).contains(1);
		assertThat(config.maxOutputTokens()).contains(2048);
		assertThat(config.stopSequences()).contains(java.util.List.of("\n\n"));
		assertThat(config.responseMimeType()).contains("text/plain");
		assertThat(config.presencePenalty()).contains(0.1f);
		assertThat(config.frequencyPenalty()).contains(0.2f);
		assertThat(config.seed()).contains(42);
	}

	@Test
	void nullTemperaturePassesNullConfig() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("sample.wav")));

		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.mockModels).generateContent(anyString(), any(Content.class), configCaptor.capture());

		assertThat(configCaptor.getValue()).isNull();
	}

	@Test
	void responseIsMappedToTranscriptionAndMetadata() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("hello world", "gemini-3.5-transcribe-001", 12, 7, 19));

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(audioResource("sample.wav")));

		assertThat(response.getResult().getOutput()).isEqualTo("hello world");

		assertThat(response.getMetadata()).isInstanceOf(GoogleGenAiAudioTranscriptionMetadata.class);
		GoogleGenAiAudioTranscriptionMetadata metadata = (GoogleGenAiAudioTranscriptionMetadata) response.getMetadata();
		assertThat(metadata.getModelVersion()).isEqualTo("gemini-3.5-transcribe-001");

		Usage usage = metadata.getUsage();
		assertThat(usage.getPromptTokens()).isEqualTo(12);
		assertThat(usage.getCompletionTokens()).isEqualTo(7);
		assertThat(usage.getTotalTokens()).isEqualTo(19);
	}

	@Test
	void runtimeModelOptionOverridesDefaultModel() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willReturn(buildResponse("text", "model", 1, 1, 2));

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("custom-transcribe-model")
			.build();

		this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource("sample.wav"), options));

		ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.mockModels).generateContent(modelCaptor.capture(), any(Content.class),
				nullable(GenerateContentConfig.class));

		assertThat(modelCaptor.getValue()).isEqualTo("custom-transcribe-model");
	}

	private static Resource audioResource(String filename) {
		return new ByteArrayResource("fake-audio".getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}

	private static GenerateContentResponse buildResponse(String text, String modelVersion, int promptTokens,
			int candidatesTokens, int totalTokens) {
		return GenerateContentResponse.builder()
			.candidates(Candidate.builder()
				.content(Content.builder().role("model").parts(Part.fromText(text)).build())
				.build())
			.usageMetadata(GenerateContentResponseUsageMetadata.builder()
				.promptTokenCount(promptTokens)
				.candidatesTokenCount(candidatesTokens)
				.totalTokenCount(totalTokens)
				.build())
			.modelVersion(modelVersion)
			.build();
	}

}
