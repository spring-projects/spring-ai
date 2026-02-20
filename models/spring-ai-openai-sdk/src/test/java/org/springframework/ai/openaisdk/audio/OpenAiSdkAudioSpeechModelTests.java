/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.openaisdk.audio;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechModel;
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OpenAiSdkAudioSpeechModel.
 *
 * @author Ilayaperumal Gopinathan
 */
@ExtendWith(MockitoExtension.class)
class OpenAiSdkAudioSpeechModelTests {

	@Mock
	private OpenAIClient mockClient;

	@Test
	void testModelCreation() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();
		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
	}

	@Test
	void testDefaultConstructor() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
		assertThat(model.getDefaultOptions()).isInstanceOf(OpenAiSdkAudioSpeechOptions.class);
	}

	@Test
	void testConstructorWithClient() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
	}

	@Test
	void testConstructorWithClientAndOptions() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.NOVA)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isEqualTo(options);
	}

	@Test
	void testConstructorWithAllParameters() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.SHIMMER)
			.speed(1.5)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isEqualTo(options);
	}

	@Test
	void testDefaultOptions() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();
		OpenAiSdkAudioSpeechOptions options = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();

		assertThat(options.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(options.getVoice()).isEqualTo("alloy");
		assertThat(options.getResponseFormat()).isEqualTo("mp3");
		assertThat(options.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testDefaultOptionsValues() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();
		TextToSpeechOptions options = model.getDefaultOptions();

		assertThat(options).isInstanceOf(OpenAiSdkAudioSpeechOptions.class);

		OpenAiSdkAudioSpeechOptions sdkOptions = (OpenAiSdkAudioSpeechOptions) options;
		assertThat(sdkOptions.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(sdkOptions.getVoice()).isEqualTo("alloy");
		assertThat(sdkOptions.getResponseFormat()).isEqualTo("mp3");
		assertThat(sdkOptions.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testNullTextHandling() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThatThrownBy(() -> model.call((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Text must not be null");
	}

	@Test
	void testEmptyTextHandling() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThatThrownBy(() -> model.call("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Text must not be null or empty");
	}

	@Test
	void testNullPromptHandling() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThatThrownBy(() -> model.call((org.springframework.ai.audio.tts.TextToSpeechPrompt) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Prompt must not be null");
	}

	@Test
	void testOptionsBuilder() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.ECHO)
			.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(2.0)
			.build();

		assertThat(options.getModel()).isEqualTo("tts-1");
		assertThat(options.getVoice()).isEqualTo("echo");
		assertThat(options.getResponseFormat()).isEqualTo("opus");
		assertThat(options.getSpeed()).isEqualTo(2.0);
	}

	@Test
	void testAllVoiceConstants() {
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.ALLOY.getValue()).isEqualTo("alloy");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.ECHO.getValue()).isEqualTo("echo");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.FABLE.getValue()).isEqualTo("fable");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.ONYX.getValue()).isEqualTo("onyx");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.NOVA.getValue()).isEqualTo("nova");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.SHIMMER.getValue()).isEqualTo("shimmer");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.SAGE.getValue()).isEqualTo("sage");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.CORAL.getValue()).isEqualTo("coral");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.BALLAD.getValue()).isEqualTo("ballad");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.VERSE.getValue()).isEqualTo("verse");
		assertThat(OpenAiSdkAudioSpeechOptions.Voice.ASH.getValue()).isEqualTo("ash");
	}

	@Test
	void testAllAudioFormatConstants() {
		assertThat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.MP3.getValue()).isEqualTo("mp3");
		assertThat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.OPUS.getValue()).isEqualTo("opus");
		assertThat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.AAC.getValue()).isEqualTo("aac");
		assertThat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.FLAC.getValue()).isEqualTo("flac");
		assertThat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.WAV.getValue()).isEqualTo("wav");
		assertThat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.PCM.getValue()).isEqualTo("pcm");
	}

	@Test
	void testOptionsMerging() {
		OpenAiSdkAudioSpeechOptions source = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.NOVA)
			.speed(1.5)
			.build();

		OpenAiSdkAudioSpeechOptions target = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.WAV)
			.speed(1.0)
			.build();

		// Create model with target defaults
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(target)
			.build();

		// Verify that default options are set
		OpenAiSdkAudioSpeechOptions defaults = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaults.getModel()).isEqualTo("tts-1");
		assertThat(defaults.getVoice()).isEqualTo("alloy");
		assertThat(defaults.getSpeed()).isEqualTo(1.0);
		assertThat(defaults.getResponseFormat()).isEqualTo("wav");
	}

	@Test
	void testBuilder() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.SHIMMER)
			.speed(1.5)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isEqualTo(options);
	}

	@Test
	void testBuilderWithDefaults() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
		assertThat(model.getDefaultOptions()).isInstanceOf(OpenAiSdkAudioSpeechOptions.class);

		OpenAiSdkAudioSpeechOptions defaults = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaults.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(defaults.getVoice()).isEqualTo("alloy");
		assertThat(defaults.getResponseFormat()).isEqualTo("mp3");
		assertThat(defaults.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testBuilderMutate() {
		OpenAiSdkAudioSpeechOptions originalOptions = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.ALLOY)
			.build();

		OpenAiSdkAudioSpeechModel originalModel = OpenAiSdkAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(originalOptions)
			.build();

		// Create a modified copy using mutate
		OpenAiSdkAudioSpeechOptions newOptions = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.NOVA)
			.build();

		OpenAiSdkAudioSpeechModel modifiedModel = originalModel.mutate().defaultOptions(newOptions).build();

		// Verify original model is unchanged
		OpenAiSdkAudioSpeechOptions originalDefaults = (OpenAiSdkAudioSpeechOptions) originalModel.getDefaultOptions();
		assertThat(originalDefaults.getModel()).isEqualTo("tts-1");
		assertThat(originalDefaults.getVoice()).isEqualTo("alloy");

		// Verify modified model has new options
		OpenAiSdkAudioSpeechOptions modifiedDefaults = (OpenAiSdkAudioSpeechOptions) modifiedModel.getDefaultOptions();
		assertThat(modifiedDefaults.getModel()).isEqualTo("tts-1-hd");
		assertThat(modifiedDefaults.getVoice()).isEqualTo("nova");
	}

	@Test
	void testBuilderWithPartialOptions() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		OpenAiSdkAudioSpeechOptions defaults = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaults.getModel()).isEqualTo("gpt-4o-mini-tts");
	}

}
