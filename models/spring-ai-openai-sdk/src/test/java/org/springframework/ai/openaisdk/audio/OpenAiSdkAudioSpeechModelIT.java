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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechModel;
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechOptions;
import org.springframework.ai.openaisdk.metadata.OpenAiSdkAudioSpeechResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAiSdkAudioSpeechModel.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ahmed Yousri
 * @author Jonghoon Park
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiSdkAudioSpeechModelIT {

	@Test
	void testModelCreation() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().build();
		assertThat(model).isNotNull();
		assertThat(model.getDefaultOptions()).isNotNull();
	}

	@Test
	void testSimpleSpeechGeneration() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello world");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		Speech speech = response.getResult();
		assertThat(speech).isNotNull();
		assertThat(speech.getOutput()).isNotEmpty();
	}

	@Test
	void testDefaultOptions() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().build();
		OpenAiSdkAudioSpeechOptions options = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();

		assertThat(options.getModel()).isEqualTo("gpt-4o-mini-tts");
		assertThat(options.getVoice()).isEqualTo("alloy");
		assertThat(options.getResponseFormat()).isEqualTo("mp3");
		assertThat(options.getSpeed()).isEqualTo(1.0);
	}

	@Test
	void testCustomOptions() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.NOVA)
			.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(1.5)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing custom options");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testNewVoiceOptions() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.BALLAD)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing new voice");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testNewFormatOptions() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.WAV)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing WAV format");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testSimpleStringInput() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().build();
		byte[] audioBytes = model.call("Today is a wonderful day to build something people love!");

		assertThat(audioBytes).isNotEmpty();
	}

	@Test
	void testStreamingBehavior() {
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		Flux<TextToSpeechResponse> responseFlux = model.stream(prompt);

		assertThat(responseFlux).isNotNull();
		List<TextToSpeechResponse> responses = responseFlux.collectList().block();
		assertThat(responses).isNotNull();

		// SDK doesn't support true streaming - should return single response
		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).getResult().getOutput()).isNotEmpty();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "alloy", "echo", "fable", "onyx", "nova", "shimmer", "sage", "coral", "ash" })
	void testAllVoices(String voice) {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(voice)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testRateLimitMetadata() {
		// Verify that SDK extracts rate limit metadata from response headers
		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);
		OpenAiSdkAudioSpeechResponseMetadata metadata = (OpenAiSdkAudioSpeechResponseMetadata) response.getMetadata();

		// Metadata should be present with rate limit information
		assertThat(metadata).isNotNull();
		assertThat(metadata.getRateLimit()).isNotNull();

		// Rate limit values should be populated from response headers
		boolean hasRateLimitData = metadata.getRateLimit().getRequestsLimit() != null
				|| metadata.getRateLimit().getTokensLimit() != null;
		assertThat(hasRateLimitData).isTrue();
	}

	@Test
	void testTts1Model() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.WAV)
			.speed(1.0)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testTts1HdModel() {
		OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiSdkAudioSpeechOptions.Voice.SHIMMER)
			.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(1.0)
			.build();

		OpenAiSdkAudioSpeechModel model = OpenAiSdkAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing high definition audio model");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

}
