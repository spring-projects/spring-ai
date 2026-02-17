/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.openai.audio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author Ilayaperumal Gopinathan
 * @author Jonghoon Park
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAudioModelNoOpApiKeysIT {

	@Autowired
	private OpenAiAudioApi audioApi;

	@Test
	void checkNoOpKey() {
		assertThatThrownBy(() -> this.audioApi
			.createSpeech(OpenAiAudioApi.SpeechRequest.builder()
				.model(OpenAiAudioApi.TtsModel.GPT_4_O_MINI_TTS.getValue())
				.input("Hello, my name is Chris and I love Spring A.I.")
				.voice(OpenAiAudioApi.SpeechRequest.Voice.ONYX.getValue())
				.build())
			.getBody()).isInstanceOf(NonTransientAiException.class);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiAudioApi openAiAudioApi() {
			return OpenAiAudioApi.builder()
				.apiKey(new NoopApiKey())
				.speechPath("/v1/audio/speech")
				.transcriptionPath("/v1/audio/transcriptions")
				.build();
		}

	}

}
