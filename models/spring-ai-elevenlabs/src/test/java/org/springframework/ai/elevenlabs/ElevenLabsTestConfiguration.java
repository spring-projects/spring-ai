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

package org.springframework.ai.elevenlabs;

import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;
import org.springframework.ai.elevenlabs.api.ElevenLabsVoicesApi;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Configuration class for the ElevenLabs API.
 *
 * @author Alexandros Pappas
 */
@SpringBootConfiguration
public class ElevenLabsTestConfiguration {

	@Bean
	public ElevenLabsApi elevenLabsApi() {
		return ElevenLabsApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public ElevenLabsVoicesApi elevenLabsVoicesApi() {
		return ElevenLabsVoicesApi.builder().apiKey(getApiKey()).build();
	}

	private SimpleApiKey getApiKey() {
		String apiKey = System.getenv("ELEVEN_LABS_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name ELEVEN_LABS_API_KEY");
		}
		return new SimpleApiKey(apiKey);
	}

	@Bean
	public ElevenLabsTextToSpeechModel elevenLabsSpeechModel() {
		return ElevenLabsTextToSpeechModel.builder().elevenLabsApi(elevenLabsApi()).build();
	}

	@Bean
	public ElevenLabsSpeechToTextApi elevenLabsSpeechToTextApi() {
		return ElevenLabsSpeechToTextApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public ElevenLabsAudioTranscriptionModel elevenLabsAudioTranscriptionModel() {
		return ElevenLabsAudioTranscriptionModel.builder().api(elevenLabsSpeechToTextApi()).build();
	}

}
