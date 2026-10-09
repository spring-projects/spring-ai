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

package org.springframework.ai.google.genai.tts;

import java.util.List;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.SpeechConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoogleGenAiTextToSpeechModel}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTextToSpeechModelTests {

	private static final byte[] AUDIO = new byte[] { 1, 2, 3, 4 };

	private Models models;

	private GoogleGenAiTextToSpeechConnectionDetails connectionDetails;

	@BeforeEach
	void setUp() {
		this.models = mock(Models.class);
		when(this.models.generateContent(anyString(), any(Content.class), any(GenerateContentConfig.class)))
			.thenReturn(audioResponse(AUDIO));
		Client client = mock(Client.class);
		ReflectionTestUtils.setField(client, "models", this.models);
		this.connectionDetails = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.projectId("my-project")
			.genAiClient(client)
			.build();
	}

	private static GenerateContentResponse audioResponse(byte[] audio) {
		return GenerateContentResponse.builder()
			.candidates(List.of(Candidate.builder()
				.content(Content.builder()
					.parts(Part.builder().inlineData(Blob.builder().data(audio).mimeType("audio/L16").build()).build())
					.build())
				.build()))
			.build();
	}

	@Test
	void singleSpeakerMapsInputAndVoice() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.languageCode("en")
			.stylePrompt("Say the following in a curious way")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		TextToSpeechResponse response = model.call(new TextToSpeechPrompt("Hello world", options));

		// the default LINEAR16 encoding wraps the raw PCM in a WAV header
		byte[] output = response.getResult().getOutput();
		assertThat(output).startsWith("RIFF".getBytes());
		assertThat(output.length).isEqualTo(44 + AUDIO.length);

		ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.models).generateContent(modelCaptor.capture(), contentCaptor.capture(), configCaptor.capture());

		assertThat(modelCaptor.getValue()).isEqualTo("gemini-2.5-flash-tts");
		assertThat(contentCaptor.getValue().parts()).isPresent();
		assertThat(contentCaptor.getValue().parts().get().get(0).text())
			.hasValue("Say the following in a curious way: Hello world");

		GenerateContentConfig config = configCaptor.getValue();
		assertThat(config.responseModalities()).hasValue(List.of("AUDIO"));
		SpeechConfig speechConfig = config.speechConfig().orElseThrow();
		assertThat(speechConfig.languageCode()).hasValue("en");
		assertThat(speechConfig.voiceConfig().orElseThrow().prebuiltVoiceConfig().orElseThrow().voiceName())
			.hasValue("Kore");
		assertThat(speechConfig.multiSpeakerVoiceConfig()).isEmpty();
	}

	@Test
	void pcmEncodingReturnsRawAudio() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.voiceName("Kore")
			.audioEncoding(GoogleGenAiAudioSpeechOptions.AudioEncoding.PCM)
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		TextToSpeechResponse response = model.call(new TextToSpeechPrompt("Hello", options));

		assertThat(response.getResult().getOutput()).isEqualTo(AUDIO);
	}

	@Test
	void defaultVoiceIsUsedWhenUnset() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hello", options));

		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.models).generateContent(anyString(), any(Content.class), configCaptor.capture());
		assertThat(configCaptor.getValue()
			.speechConfig()
			.orElseThrow()
			.voiceConfig()
			.orElseThrow()
			.prebuiltVoiceConfig()
			.orElseThrow()
			.voiceName()).hasValue(GoogleGenAiAudioSpeechOptions.DEFAULT_VOICE);
	}

	@Test
	void replicatedVoiceIsMapped() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.replicatedVoice(new GoogleGenAiAudioSpeechOptions.ReplicatedVoice(new byte[] { 1 }, new byte[] { 2 }, null,
					"signature"))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hello", options));

		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.models).generateContent(anyString(), any(Content.class), configCaptor.capture());

		var replicated = configCaptor.getValue()
			.speechConfig()
			.orElseThrow()
			.voiceConfig()
			.orElseThrow()
			.replicatedVoiceConfig()
			.orElseThrow();
		assertThat(replicated.mimeType()).hasValue("audio/wav");
		assertThat(replicated.voiceSampleAudio().orElseThrow()).isEqualTo(new byte[] { 1 });
		assertThat(replicated.consentAudio().orElseThrow()).isEqualTo(new byte[] { 2 });
		assertThat(replicated.voiceConsentSignature().orElseThrow().signature()).hasValue("signature");
	}

	@Test
	void multiSpeakerMapsSpeakerVoiceConfigsAndTurns() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.speakerVoiceConfigs(
					List.of(new SpeakerVoiceConfig("Sam", "Kore"), new SpeakerVoiceConfig("Bob", "Charon")))
			.multiSpeakerTurns(List.of(new GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn("Sam", "How's it going?"),
					new GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn("Bob", "Not too bad.")))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("ignored", options));

		ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.models).generateContent(anyString(), contentCaptor.capture(), configCaptor.capture());

		assertThat(contentCaptor.getValue().parts().orElseThrow().get(0).text())
			.hasValue("Sam: How's it going?\nBob: Not too bad.\n");

		SpeechConfig speechConfig = configCaptor.getValue().speechConfig().orElseThrow();
		assertThat(speechConfig.voiceConfig()).isEmpty();
		var speakerConfigs = speechConfig.multiSpeakerVoiceConfig().orElseThrow().speakerVoiceConfigs().orElseThrow();
		assertThat(speakerConfigs).hasSize(2);
		assertThat(speakerConfigs.get(0).speaker()).hasValue("Sam");
		assertThat(speakerConfigs.get(0).voiceConfig().orElseThrow().prebuiltVoiceConfig().orElseThrow().voiceName())
			.hasValue("Kore");
		assertThat(speakerConfigs.get(1).speaker()).hasValue("Bob");
		assertThat(speakerConfigs.get(1).voiceConfig().orElseThrow().prebuiltVoiceConfig().orElseThrow().voiceName())
			.hasValue("Charon");
	}

	@Test
	void runtimeOptionsOverrideDefaults() {
		GoogleGenAiAudioSpeechOptions defaults = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, defaults);

		GoogleGenAiAudioSpeechOptions runtime = GoogleGenAiAudioSpeechOptions.builder().voiceName("Leda").build();
		model.call(new TextToSpeechPrompt("Hi", runtime));

		ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
		verify(this.models).generateContent(modelCaptor.capture(), any(Content.class), configCaptor.capture());

		// runtime voiceName overrides the default, the model falls back to the default
		assertThat(configCaptor.getValue()
			.speechConfig()
			.orElseThrow()
			.voiceConfig()
			.orElseThrow()
			.prebuiltVoiceConfig()
			.orElseThrow()
			.voiceName()).hasValue("Leda");
		assertThat(modelCaptor.getValue()).isEqualTo("gemini-2.5-flash-tts");
	}

	@Test
	void emptyPromptTextIsRejected() {
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails,
				GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build());

		assertThatThrownBy(() -> model.call(new TextToSpeechPrompt(""))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void responseWithoutAudioIsRejected() {
		when(this.models.generateContent(anyString(), any(Content.class), any(GenerateContentConfig.class)))
			.thenReturn(GenerateContentResponse.builder()
				.candidates(List.of(Candidate.builder()
					.content(Content.builder().parts(Part.fromText("no audio")).build())
					.build()))
				.build());
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails,
				GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build());

		assertThatThrownBy(() -> model.call(new TextToSpeechPrompt("Hi"))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no audio data");
	}

	@Test
	void usageReflectsInputTextAndOutputAudioSize() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		TextToSpeechResponse response = model.call(new TextToSpeechPrompt("Hello world", options));

		int outputLength = 44 + AUDIO.length; // WAV header + PCM
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo("Hello world".length());
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(outputLength);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo("Hello world".length() + outputLength);
	}

	@Test
	void getOptionsReturnsDefaults() {
		GoogleGenAiAudioSpeechOptions defaults = GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, defaults);

		assertThat(model.getOptions()).isSameAs(defaults);
	}

}
