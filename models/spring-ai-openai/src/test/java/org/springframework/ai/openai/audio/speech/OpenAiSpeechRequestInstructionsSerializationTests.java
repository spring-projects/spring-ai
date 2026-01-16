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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.api.OpenAiAudioApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the OpenAI audio SpeechRequest includes an 'instructions' field when it
 * is provided.
 */
class OpenAiSpeechRequestInstructionsSerializationTests {

	@Test
	void speechRequestIncludesInstructionsFieldWhenProvided() throws Exception {
		OpenAiAudioApi.SpeechRequest request = OpenAiAudioApi.SpeechRequest.builder()
			.model(OpenAiAudioApi.TtsModel.GPT_4_O_MINI_TTS.getValue())
			.input("Hello Spring AI")
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.speed(1.0)
			.instructions("Please speak in a calm, warm tone")
			.build();

		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(request);

		assertThat(json).contains("\"instructions\"");
		assertThat(json).contains("calm, warm tone");
	}

}
