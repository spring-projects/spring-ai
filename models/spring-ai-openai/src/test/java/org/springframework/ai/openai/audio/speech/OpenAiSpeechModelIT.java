/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openai.audio.speech;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioSpeechResponseMetadata;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ahmed Yousri
 * @author Jonghoon Park
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiSpeechModelIT extends AbstractIT {

	private static final Float SPEED = 1.0f;

	@Test
	void shouldSuccessfullyStreamAudioBytesForEmptyMessage() {
		Flux<byte[]> response = this.speechModel.stream("Today is a wonderful day to build something people love!");
		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
		System.out.println(response.collectList().block());
	}

	@Test
	void shouldProduceAudioBytesDirectlyFromMessage() {
		byte[] audioBytes = this.speechModel.call("Today is a wonderful day to build something people love!");
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

	@Test
	void shouldGenerateNonEmptyMp3AudioFromSpeechPrompt() {
		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
			.speed(SPEED)
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.model(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		SpeechResponse response = this.speechModel.call(speechPrompt);
		byte[] audioBytes = response.getResult().getOutput();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

	@Test
	void shouldGenerateNonEmptyWavAudioFromSpeechPrompt() {
		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
			.speed(SPEED)
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.WAV)
			.model(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		SpeechResponse response = this.speechModel.call(speechPrompt);
		byte[] audioBytes = response.getResult().getOutput();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

	@Test
	void speechRateLimitTest() {
		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
			.speed(SPEED)
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.model(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		SpeechResponse response = this.speechModel.call(speechPrompt);
		OpenAiAudioSpeechResponseMetadata metadata = response.getMetadata();
		assertThat(metadata).isNotNull();
		assertThat(metadata.getRateLimit()).isNotNull();
		assertThat(metadata.getRateLimit().getRequestsLimit()).isPositive();
		assertThat(metadata.getRateLimit().getRequestsLimit()).isPositive();

	}

	@Test
	void shouldStreamNonEmptyResponsesForValidSpeechPrompts() {

		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
			.speed(SPEED)
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.model(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();

		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		Flux<SpeechResponse> responseFlux = this.speechModel.stream(speechPrompt);
		assertThat(responseFlux).isNotNull();
		List<SpeechResponse> responses = responseFlux.collectList().block();
		assertThat(responses).isNotNull();
		responses.forEach(response ->
		// System.out.println("Audio data chunk size: " +
		// response.getResult().getOutput().length);
		assertThat(response.getResult().getOutput()).isNotEmpty());
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "alloy", "echo", "fable", "onyx", "nova", "shimmer", "sage", "coral", "ash" })
	void speechVoicesTest(String voice) {
		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.voice(voice)
			.speed(SPEED)
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.model(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		SpeechResponse response = this.speechModel.call(speechPrompt);
		byte[] audioBytes = response.getResult().getOutput();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(audioBytes).hasSizeGreaterThan(0);
	}

}
