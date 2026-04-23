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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.metadata.OpenAiAudioSpeechResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAiAudioSpeechModel.
 *
 * @author Ahmed Yousri
 * @author Jonghoon Park
 * @author Ilayaperumal Gopinathan
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioSpeechModelIT {

	@Test
	void testSimpleSpeechGeneration() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello world");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		Speech speech = response.getResult();
		assertThat(speech).isNotNull();
		assertThat(speech.getOutput()).isNotEmpty();
	}

	@Test
	void testCustomOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.NOVA)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(1.5)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().defaultOptions(options).build();

		// Verify that the custom options were set on the model
		OpenAiAudioSpeechOptions defaultOptions = (OpenAiAudioSpeechOptions) model.getDefaultOptions();
		assertThat(defaultOptions.getModel()).isEqualTo("tts-1-hd");
		assertThat(defaultOptions.getVoice()).isEqualTo("nova");
		assertThat(defaultOptions.getResponseFormat()).isEqualTo("opus");
		assertThat(defaultOptions.getSpeed()).isEqualTo(1.5);

		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing custom options");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testNewVoiceOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiAudioSpeechOptions.Voice.BALLAD)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing new voice");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testNewFormatOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing WAV format");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testSimpleStringInput() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		byte[] audioBytes = model.call("Today is a wonderful day to build something people love!");

		assertThat(audioBytes).isNotEmpty();
	}

	@Test
	void testStreamingBehavior() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
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
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(voice)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testRateLimitMetadata() {
		// Verify that SDK extracts rate limit metadata from response headers
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);
		OpenAiAudioSpeechResponseMetadata metadata = (OpenAiAudioSpeechResponseMetadata) response.getMetadata();

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
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
			.speed(1.0)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testTts1HdModel() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.SHIMMER)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(1.0)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().defaultOptions(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing high definition audio model");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

}
