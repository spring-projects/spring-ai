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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.mistralai.transcription.MistralAiTranscriptionResponseMetadata;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MistralAiTranscriptionAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiTranscriptionAutoConfigurationIT {

	private static final Resource AUDIO = new ClassPathResource("/speech.flac");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.api-key=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(
				AutoConfigurations.of(MistralAiTranscriptionAutoConfiguration.class, RestClientAutoConfiguration.class,
						SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class));

	@Test
	void transcribe() {
		this.contextRunner
			.withPropertyValues("spring.ai.mistralai.audio.transcription.model=voxtral-mini-transcribe-3",
					"spring.ai.mistralai.audio.transcription.timestamp-granularities=segment")
			.run(context -> {
				TranscriptionModel transcriptionModel = context.getBean(TranscriptionModel.class);

				AudioTranscriptionResponse response = transcriptionModel.call(new AudioTranscriptionPrompt(AUDIO));

				assertThat(response.getResult().getOutput()).isNotBlank();
				assertThat(response.getMetadata()).isInstanceOfSatisfying(MistralAiTranscriptionResponseMetadata.class,
						metadata -> {
							assertThat(metadata.getModel()).startsWith("voxtral-mini");
							assertThat(metadata.getSegments()).isNotEmpty();
						});
			});
	}

	@Test
	void streamTranscribe() {
		this.contextRunner.run(context -> {
			TranscriptionModel transcriptionModel = context.getBean(TranscriptionModel.class);

			List<String> chunks = transcriptionModel.streamTranscribe(AUDIO).collectList().block(Duration.ofMinutes(1));

			assertThat(chunks).isNotEmpty();
			assertThat(String.join("", chunks)).isNotBlank();
		});
	}

}
