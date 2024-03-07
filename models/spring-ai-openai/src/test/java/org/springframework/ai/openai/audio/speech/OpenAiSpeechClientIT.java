/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.openai.audio.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioSpeechResponseMetadata;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiSpeechClientIT extends AbstractIT {

	private static final Float SPEED = 1.0f;

	@Test
	void shouldSuccessfullyStreamAudioBytesForEmptyMessage() {
		Flux<byte[]> response = openAiAudioSpeechClient
			.stream("Today is a wonderful day to build something people love!");
		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
		System.out.println(response.collectList().block());
	}

	@Test
	void shouldProduceAudioBytesDirectlyFromMessage() {
		byte[] audioBytes = openAiAudioSpeechClient.call("Today is a wonderful day to build something people love!");
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

	@Test
	void shouldGenerateNonEmptyMp3AudioFromSpeechPrompt() {
		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.withVoice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
			.withSpeed(SPEED)
			.withResponseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.withModel(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		SpeechResponse response = openAiAudioSpeechClient.call(speechPrompt);
		byte[] audioBytes = response.getResult().getOutput();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

	@Test
	void speechRateLimitTest() {
		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.withVoice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
			.withSpeed(SPEED)
			.withResponseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.withModel(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		SpeechResponse response = openAiAudioSpeechClient.call(speechPrompt);
		OpenAiAudioSpeechResponseMetadata metadata = response.getMetadata();
		assertThat(metadata).isNotNull();
		assertThat(metadata.getRateLimit()).isNotNull();
		assertThat(metadata.getRateLimit().getRequestsLimit()).isPositive();
		assertThat(metadata.getRateLimit().getRequestsLimit()).isPositive();

	}

	@Test
	void shouldStreamNonEmptyResponsesForValidSpeechPrompts() {


		OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
			.withVoice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
			.withSpeed(SPEED)
			.withResponseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.withModel(OpenAiAudioApi.TtsModel.TTS_1.value)
			.build();

		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!",
				speechOptions);
		Flux<SpeechResponse> responseFlux = openAiAudioSpeechClient.stream(speechPrompt);
		assertThat(responseFlux).isNotNull();
		List<SpeechResponse> responses = responseFlux.collectList().block();
		assertThat(responses).isNotNull();
		responses.forEach(response -> {
			System.out.println("Audio data chunk size: " + response.getResult().getOutput().length);
			assertThat(response.getResult().getOutput()).isNotEmpty();
		});
	}

}