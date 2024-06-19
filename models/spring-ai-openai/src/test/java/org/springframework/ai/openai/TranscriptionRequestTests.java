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
package org.springframework.ai.openai;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest.GranularityType;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class TranscriptionRequestTests {

	@Test
	public void defaultOptions() {

		var client = new OpenAiAudioTranscriptionModel(new OpenAiAudioApi("TEST"),
				OpenAiAudioTranscriptionOptions.builder()
					.withModel("DEFAULT_MODEL")
					.withResponseFormat(TranscriptResponseFormat.TEXT)
					.withLanguage("en")
					.withPrompt("Prompt1")
					.withGranularityType(GranularityType.WORD)
					.withTemperature(66.6f)
					.build());

		var request = client.createRequestBody(
				new AudioTranscriptionPrompt(new DefaultResourceLoader().getResource("classpath:/test.png")));

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.responseFormat()).isEqualByComparingTo(TranscriptResponseFormat.TEXT);
		assertThat(request.temperature()).isEqualTo(66.6f);
		assertThat(request.prompt()).isEqualTo("Prompt1");
		assertThat(request.language()).isEqualTo("en");
		assertThat(request.granularityType()).isEqualTo(GranularityType.WORD);
	}

	@Test
	public void runtimeOptions() {

		var client = new OpenAiAudioTranscriptionModel(new OpenAiAudioApi("TEST"),
				OpenAiAudioTranscriptionOptions.builder()
					.withModel("DEFAULT_MODEL")
					.withResponseFormat(TranscriptResponseFormat.TEXT)
					.withLanguage("en")
					.withPrompt("Prompt1")
					.withGranularityType(GranularityType.WORD)
					.withTemperature(66.6f)
					.build());

		var request = client.createRequestBody(
				new AudioTranscriptionPrompt(new DefaultResourceLoader().getResource("classpath:/test.png"),
						OpenAiAudioTranscriptionOptions.builder()
							.withModel("RUNTIME_MODEL")
							.withResponseFormat(TranscriptResponseFormat.JSON)
							.withLanguage("bg")
							.withPrompt("Prompt2")
							.withGranularityType(GranularityType.SEGMENT)
							.withTemperature(99.9f)
							.build()));

		assertThat(request.model()).isEqualTo("RUNTIME_MODEL");
		assertThat(request.responseFormat()).isEqualByComparingTo(TranscriptResponseFormat.JSON);
		assertThat(request.temperature()).isEqualTo(99.9f);
		assertThat(request.prompt()).isEqualTo("Prompt2");
		assertThat(request.language()).isEqualTo("bg");
		assertThat(request.granularityType()).isEqualTo(GranularityType.SEGMENT);
	}

}
