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

package org.springframework.ai.openai.audio;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OpenAiAudioSpeechModel.
 *
 * @author Ilayaperumal Gopinathan
 */
@ExtendWith(MockitoExtension.class)
class OpenAiAudioSpeechModelTests {

	@Mock
	private OpenAIClient mockClient;

	@Test
	void testModelCreation() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();
		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
	}

	@Test
	void testDefaultConstructor() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
		assertThat(model.getDefaultOptions()).isInstanceOf(OpenAiAudioSpeechOptions.class);
	}

	@Test
	void testConstructorWithClient() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
	}

	@Test
	void testConstructorWithClientAndOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiAudioSpeechOptions.Voice.NOVA)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isEqualTo(options);
	}

	@Test
	void testConstructorWithAllParameters() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.SHIMMER)
			.speed(1.5)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isEqualTo(options);
	}

	@Test
	void testDefaultOptions() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();
		OpenAiAudioSpeechOptions options = (OpenAiAudioSpeechOptions) model.getDefaultOptions();

		assertThat(options.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(options.getVoice()).isEqualTo("alloy");
		assertThat(options.getResponseFormat()).isEqualTo("mp3");
		assertThat(options.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testDefaultOptionsValues() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();
		TextToSpeechOptions options = model.getDefaultOptions();

		assertThat(options).isInstanceOf(OpenAiAudioSpeechOptions.class);

		OpenAiAudioSpeechOptions sdkOptions = (OpenAiAudioSpeechOptions) options;
		assertThat(sdkOptions.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(sdkOptions.getVoice()).isEqualTo("alloy");
		assertThat(sdkOptions.getResponseFormat()).isEqualTo("mp3");
		assertThat(sdkOptions.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testNullTextHandling() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThatThrownBy(() -> model.call((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Text must not be null");
	}

	@Test
	void testEmptyTextHandling() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThatThrownBy(() -> model.call("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Text must not be null or empty");
	}

	@Test
	void testNullPromptHandling() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThatThrownBy(() -> model.call((org.springframework.ai.audio.tts.TextToSpeechPrompt) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Prompt must not be null");
	}

	@Test
	void testOptionsBuilder() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiAudioSpeechOptions.Voice.ECHO)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(2.0)
			.build();

		assertThat(options.getModel()).isEqualTo("tts-1");
		assertThat(options.getVoice()).isEqualTo("echo");
		assertThat(options.getResponseFormat()).isEqualTo("opus");
		assertThat(options.getSpeed()).isEqualTo(2.0);
	}

	@Test
	void testAllVoiceConstants() {
		assertThat(OpenAiAudioSpeechOptions.Voice.ALLOY.getValue()).isEqualTo("alloy");
		assertThat(OpenAiAudioSpeechOptions.Voice.ECHO.getValue()).isEqualTo("echo");
		assertThat(OpenAiAudioSpeechOptions.Voice.FABLE.getValue()).isEqualTo("fable");
		assertThat(OpenAiAudioSpeechOptions.Voice.ONYX.getValue()).isEqualTo("onyx");
		assertThat(OpenAiAudioSpeechOptions.Voice.NOVA.getValue()).isEqualTo("nova");
		assertThat(OpenAiAudioSpeechOptions.Voice.SHIMMER.getValue()).isEqualTo("shimmer");
		assertThat(OpenAiAudioSpeechOptions.Voice.SAGE.getValue()).isEqualTo("sage");
		assertThat(OpenAiAudioSpeechOptions.Voice.CORAL.getValue()).isEqualTo("coral");
		assertThat(OpenAiAudioSpeechOptions.Voice.BALLAD.getValue()).isEqualTo("ballad");
		assertThat(OpenAiAudioSpeechOptions.Voice.VERSE.getValue()).isEqualTo("verse");
		assertThat(OpenAiAudioSpeechOptions.Voice.ASH.getValue()).isEqualTo("ash");
	}

	@Test
	void testAllAudioFormatConstants() {
		assertThat(OpenAiAudioSpeechOptions.AudioResponseFormat.MP3.getValue()).isEqualTo("mp3");
		assertThat(OpenAiAudioSpeechOptions.AudioResponseFormat.OPUS.getValue()).isEqualTo("opus");
		assertThat(OpenAiAudioSpeechOptions.AudioResponseFormat.AAC.getValue()).isEqualTo("aac");
		assertThat(OpenAiAudioSpeechOptions.AudioResponseFormat.FLAC.getValue()).isEqualTo("flac");
		assertThat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV.getValue()).isEqualTo("wav");
		assertThat(OpenAiAudioSpeechOptions.AudioResponseFormat.PCM.getValue()).isEqualTo("pcm");
	}

	@Test
	void testOptionsMerging() {
		OpenAiAudioSpeechOptions source = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.NOVA)
			.speed(1.5)
			.build();

		OpenAiAudioSpeechOptions target = OpenAiAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
			.speed(1.0)
			.build();

		// Create model with target defaults
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(target)
			.build();

		// Verify that default options are set
		OpenAiAudioSpeechOptions defaults = (OpenAiAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaults.getModel()).isEqualTo("tts-1");
		assertThat(defaults.getVoice()).isEqualTo("alloy");
		assertThat(defaults.getSpeed()).isEqualTo(1.0);
		assertThat(defaults.getResponseFormat()).isEqualTo("wav");
	}

	@Test
	void testBuilder() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.SHIMMER)
			.speed(1.5)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isEqualTo(options);
	}

	@Test
	void testBuilderWithDefaults() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
		assertThat(model.getDefaultOptions()).isInstanceOf(OpenAiAudioSpeechOptions.class);

		OpenAiAudioSpeechOptions defaults = (OpenAiAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaults.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(defaults.getVoice()).isEqualTo("alloy");
		assertThat(defaults.getResponseFormat()).isEqualTo("mp3");
		assertThat(defaults.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testBuilderMutate() {
		OpenAiAudioSpeechOptions originalOptions = OpenAiAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
			.build();

		OpenAiAudioSpeechModel originalModel = OpenAiAudioSpeechModel.builder()
			.openAiClient(this.mockClient)
			.defaultOptions(originalOptions)
			.build();

		// Create a modified copy using mutate
		OpenAiAudioSpeechOptions newOptions = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.NOVA)
			.build();

		OpenAiAudioSpeechModel modifiedModel = originalModel.mutate().defaultOptions(newOptions).build();

		// Verify original model is unchanged
		OpenAiAudioSpeechOptions originalDefaults = (OpenAiAudioSpeechOptions) originalModel.getDefaultOptions();
		assertThat(originalDefaults.getModel()).isEqualTo("tts-1");
		assertThat(originalDefaults.getVoice()).isEqualTo("alloy");

		// Verify modified model has new options
		OpenAiAudioSpeechOptions modifiedDefaults = (OpenAiAudioSpeechOptions) modifiedModel.getDefaultOptions();
		assertThat(modifiedDefaults.getModel()).isEqualTo("tts-1-hd");
		assertThat(modifiedDefaults.getVoice()).isEqualTo("nova");
	}

	@Test
	void testBuilderWithPartialOptions() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		OpenAiAudioSpeechOptions defaults = (OpenAiAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaults.getModel()).isEqualTo("gpt-4o-mini-tts");
	}

}
