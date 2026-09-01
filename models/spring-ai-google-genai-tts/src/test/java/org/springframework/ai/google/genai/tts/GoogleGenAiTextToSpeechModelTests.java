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

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	private TextToSpeechClient client;

	private GoogleGenAiTextToSpeechConnectionDetails connectionDetails;

	@BeforeEach
	void setUp() {
		this.client = mock(TextToSpeechClient.class);
		when(this.client.synthesizeSpeech(any(SynthesisInput.class), any(VoiceSelectionParams.class),
				any(AudioConfig.class)))
			.thenReturn(SynthesizeSpeechResponse.newBuilder().setAudioContent(ByteString.copyFrom(AUDIO)).build());
		this.connectionDetails = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.projectId("my-project")
			.textToSpeechClient(this.client)
			.build();
	}

	@Test
	void singleSpeakerMapsInputAndVoice() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.languageCode("en-us")
			.stylePrompt("Say the following in a curious way.")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		TextToSpeechResponse response = model.call(new TextToSpeechPrompt("Hello world", options));

		assertThat(response.getResult().getOutput()).isEqualTo(AUDIO);

		ArgumentCaptor<SynthesisInput> inputCaptor = ArgumentCaptor.forClass(SynthesisInput.class);
		ArgumentCaptor<VoiceSelectionParams> voiceCaptor = ArgumentCaptor.forClass(VoiceSelectionParams.class);
		ArgumentCaptor<AudioConfig> audioCaptor = ArgumentCaptor.forClass(AudioConfig.class);
		verify(this.client).synthesizeSpeech(inputCaptor.capture(), voiceCaptor.capture(), audioCaptor.capture());

		assertThat(inputCaptor.getValue().getText()).isEqualTo("Hello world");
		assertThat(inputCaptor.getValue().getPrompt()).isEqualTo("Say the following in a curious way.");
		assertThat(voiceCaptor.getValue().getName()).isEqualTo("Kore");
		assertThat(voiceCaptor.getValue().getLanguageCode()).isEqualTo("en-us");
		assertThat(voiceCaptor.getValue().getModelName()).isEqualTo("gemini-2.5-flash-tts");
		assertThat(voiceCaptor.getValue().hasMultiSpeakerVoiceConfig()).isFalse();
		assertThat(audioCaptor.getValue().getAudioEncoding()).isEqualTo(AudioEncoding.LINEAR16);
	}

	@Test
	void multiSpeakerMapsSpeakerVoiceConfigs() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.speakerVoiceConfigs(
					List.of(new SpeakerVoiceConfig("Sam", "Kore"), new SpeakerVoiceConfig("Bob", "Charon")))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Sam: Hi\nBob: Hello", options));

		ArgumentCaptor<VoiceSelectionParams> voiceCaptor = ArgumentCaptor.forClass(VoiceSelectionParams.class);
		verify(this.client).synthesizeSpeech(any(SynthesisInput.class), voiceCaptor.capture(), any(AudioConfig.class));

		VoiceSelectionParams voice = voiceCaptor.getValue();
		assertThat(voice.getName()).isEmpty();
		assertThat(voice.hasMultiSpeakerVoiceConfig()).isTrue();
		assertThat(voice.getMultiSpeakerVoiceConfig().getSpeakerVoiceConfigsList()).hasSize(2);
		assertThat(voice.getMultiSpeakerVoiceConfig().getSpeakerVoiceConfigs(0).getSpeakerAlias()).isEqualTo("Sam");
		assertThat(voice.getMultiSpeakerVoiceConfig().getSpeakerVoiceConfigs(0).getSpeakerId()).isEqualTo("Kore");
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

		ArgumentCaptor<VoiceSelectionParams> voiceCaptor = ArgumentCaptor.forClass(VoiceSelectionParams.class);
		verify(this.client).synthesizeSpeech(any(SynthesisInput.class), voiceCaptor.capture(), any(AudioConfig.class));

		// runtime voiceName overrides the default, the model falls back to the default
		assertThat(voiceCaptor.getValue().getName()).isEqualTo("Leda");
		assertThat(voiceCaptor.getValue().getModelName()).isEqualTo("gemini-2.5-flash-tts");
	}

	@Test
	void voiceNameAndSpeakerVoiceConfigsAreBothMapped() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.voiceName("Kore")
			.speakerVoiceConfigs(List.of(new SpeakerVoiceConfig("Sam", "Kore")))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hi", options));

		ArgumentCaptor<VoiceSelectionParams> voiceCaptor = ArgumentCaptor.forClass(VoiceSelectionParams.class);
		verify(this.client).synthesizeSpeech(any(SynthesisInput.class), voiceCaptor.capture(), any(AudioConfig.class));

		// both the voiceName and the speakerVoiceConfigs are set on the request, since
		// the model performs no mutual-exclusivity validation between them
		assertThat(voiceCaptor.getValue().getName()).isEqualTo("Kore");
		assertThat(voiceCaptor.getValue().hasMultiSpeakerVoiceConfig()).isTrue();
	}

	@Test
	void ssmlTakesPrecedenceOverMarkup() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.ssml("<speak>Hi</speak>")
			.markup("Hi")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hi", options));

		ArgumentCaptor<SynthesisInput> inputCaptor = ArgumentCaptor.forClass(SynthesisInput.class);
		verify(this.client).synthesizeSpeech(inputCaptor.capture(), any(VoiceSelectionParams.class),
				any(AudioConfig.class));

		// ssml is checked first in createSynthesisInput, so it takes precedence over
		// markup
		assertThat(inputCaptor.getValue().getSsml()).isEqualTo("<speak>Hi</speak>");
		assertThat(inputCaptor.getValue().getMarkup()).isEmpty();
	}

	@Test
	void ssmlTakesPrecedenceOverMultiSpeakerTurns() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.ssml("<speak>Hi</speak>")
			.multiSpeakerTurns(List.of(new GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn("Sam", "Hi")))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hi", options));

		ArgumentCaptor<SynthesisInput> inputCaptor = ArgumentCaptor.forClass(SynthesisInput.class);
		verify(this.client).synthesizeSpeech(inputCaptor.capture(), any(VoiceSelectionParams.class),
				any(AudioConfig.class));

		// ssml is checked first in createSynthesisInput, so it takes precedence over
		// multiSpeakerTurns
		assertThat(inputCaptor.getValue().getSsml()).isEqualTo("<speak>Hi</speak>");
		assertThat(inputCaptor.getValue().hasMultiSpeakerMarkup()).isFalse();
	}

	@Test
	void multiSpeakerTurnsMapToInput() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.multiSpeakerTurns(List.of(new GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn("Sam", "How's it going?"),
					new GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn("Bob", "Not too bad.")))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hi", options));

		ArgumentCaptor<SynthesisInput> inputCaptor = ArgumentCaptor.forClass(SynthesisInput.class);
		verify(this.client).synthesizeSpeech(inputCaptor.capture(), any(VoiceSelectionParams.class),
				any(AudioConfig.class));

		SynthesisInput input = inputCaptor.getValue();
		assertThat(input.getText()).isEmpty();
		assertThat(input.getMultiSpeakerMarkup().getTurnsList()).hasSize(2);
		assertThat(input.getMultiSpeakerMarkup().getTurns(0).getSpeaker()).isEqualTo("Sam");
		assertThat(input.getMultiSpeakerMarkup().getTurns(0).getText()).isEqualTo("How's it going?");
		assertThat(input.getMultiSpeakerMarkup().getTurns(1).getSpeaker()).isEqualTo("Bob");
		assertThat(input.getMultiSpeakerMarkup().getTurns(1).getText()).isEqualTo("Not too bad.");
	}

	@Test
	void ssmlOverridesPlainTextInput() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.ssml("<speak>Hello</speak>")
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("Hi", options));

		ArgumentCaptor<SynthesisInput> inputCaptor = ArgumentCaptor.forClass(SynthesisInput.class);
		verify(this.client).synthesizeSpeech(inputCaptor.capture(), any(VoiceSelectionParams.class),
				any(AudioConfig.class));

		assertThat(inputCaptor.getValue().getSsml()).isEqualTo("<speak>Hello</speak>");
		assertThat(inputCaptor.getValue().getText()).isEmpty();
	}

	@Test
	void audioConfigAndVoiceOptionsAreMapped() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.voiceName("Kore")
			.ssmlGender(GoogleGenAiAudioSpeechOptions.SsmlVoiceGender.FEMALE)
			.customVoiceModel("custom-model")
			.voiceCloningKey("cloning-key")
			.audioEncoding(GoogleGenAiAudioSpeechOptions.AudioEncoding.MP3)
			.speed(1.25)
			.pitch(-2.0)
			.volumeGainDb(3.0)
			.sampleRateHertz(24000)
			.effectsProfileIds(List.of("headphone-class-device"))
			.customPronunciations(List.of(new GoogleGenAiAudioSpeechOptions.CustomPronunciation("apple",
					GoogleGenAiAudioSpeechOptions.PhoneticEncoding.IPA, "ˈæpəl")))
			.build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, options);

		model.call(new TextToSpeechPrompt("apple", options));

		ArgumentCaptor<SynthesisInput> inputCaptor = ArgumentCaptor.forClass(SynthesisInput.class);
		ArgumentCaptor<VoiceSelectionParams> voiceCaptor = ArgumentCaptor.forClass(VoiceSelectionParams.class);
		ArgumentCaptor<AudioConfig> audioCaptor = ArgumentCaptor.forClass(AudioConfig.class);
		verify(this.client).synthesizeSpeech(inputCaptor.capture(), voiceCaptor.capture(), audioCaptor.capture());

		assertThat(inputCaptor.getValue().getCustomPronunciations().getPronunciationsList()).hasSize(1);
		assertThat(inputCaptor.getValue().getCustomPronunciations().getPronunciations(0).getPhrase())
			.isEqualTo("apple");
		assertThat(inputCaptor.getValue().getCustomPronunciations().getPronunciations(0).getPronunciation())
			.isEqualTo("ˈæpəl");

		assertThat(voiceCaptor.getValue().getSsmlGender())
			.isEqualTo(com.google.cloud.texttospeech.v1.SsmlVoiceGender.FEMALE);
		assertThat(voiceCaptor.getValue().getCustomVoice().getModel()).isEqualTo("custom-model");
		assertThat(voiceCaptor.getValue().getVoiceClone().getVoiceCloningKey()).isEqualTo("cloning-key");

		assertThat(audioCaptor.getValue().getAudioEncoding()).isEqualTo(AudioEncoding.MP3);
		assertThat(audioCaptor.getValue().getSpeakingRate()).isEqualTo(1.25);
		assertThat(audioCaptor.getValue().getPitch()).isEqualTo(-2.0);
		assertThat(audioCaptor.getValue().getVolumeGainDb()).isEqualTo(3.0);
		assertThat(audioCaptor.getValue().getSampleRateHertz()).isEqualTo(24000);
		assertThat(audioCaptor.getValue().getEffectsProfileIdList()).containsExactly("headphone-class-device");
	}

	@Test
	void emptyPromptTextIsRejected() {
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails,
				GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build());

		assertThatThrownBy(() -> model.call(new TextToSpeechPrompt(""))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getOptionsReturnsDefaults() {
		GoogleGenAiAudioSpeechOptions defaults = GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build();
		GoogleGenAiTextToSpeechModel model = new GoogleGenAiTextToSpeechModel(this.connectionDetails, defaults);

		assertThat(model.getOptions()).isSameAs(defaults);
	}

}
