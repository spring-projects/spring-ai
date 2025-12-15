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

import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the conditional passing of 'instructions' in
 * OpenAiAudioSpeechModel#createRequest(...).
 */
class OpenAiAudioSpeechModelInstructionsTests {

	private OpenAiAudioApi audioApi() {
		// Use NoopApiKey; API will not be invoked in these tests.
		return OpenAiAudioApi.builder().apiKey(new NoopApiKey()).build();
	}

	@Test
	void supportedModelIncludesInstructionsInRequest() throws Exception {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model(OpenAiAudioApi.TtsModel.GPT_4_O_MINI_TTS.getValue())
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.speed(1.0)
			.instructions("Please speak in a calm, warm tone")
			.build();

		OpenAiAudioSpeechModel model = new OpenAiAudioSpeechModel(audioApi(), options);
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello Spring AI");

		Method m = OpenAiAudioSpeechModel.class.getDeclaredMethod("createRequest", TextToSpeechPrompt.class);
		m.setAccessible(true);
		OpenAiAudioApi.SpeechRequest request = (OpenAiAudioApi.SpeechRequest) m.invoke(model, prompt);

		String json = new ObjectMapper().writeValueAsString(request);
		assertThat(json).contains("\"instructions\"");
	}

	@Test
	void unsupportedModelOmitsInstructionsInRequest() throws Exception {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model(OpenAiAudioApi.TtsModel.TTS_1.getValue())
			.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
			.responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
			.speed(1.0)
			.instructions("Please speak in a calm, warm tone")
			.build();

		OpenAiAudioSpeechModel model = new OpenAiAudioSpeechModel(audioApi(), options);
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello Spring AI");

		Method m = OpenAiAudioSpeechModel.class.getDeclaredMethod("createRequest", TextToSpeechPrompt.class);
		m.setAccessible(true);
		OpenAiAudioApi.SpeechRequest request = (OpenAiAudioApi.SpeechRequest) m.invoke(model, prompt);

		String json = new ObjectMapper().writeValueAsString(request);
		assertThat(json).doesNotContain("\"instructions\"");
	}

	// No additional wrapper required; model methods are exercised without network I/O.

}
