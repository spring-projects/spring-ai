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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Google GenAI TTS properties binding.
 *
 * @author Alexandros Pappas
 */
public class GoogleGenAiTtsPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesTestConfiguration.class);

	@Test
	void connectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=test-tts-key",
					"spring.ai.google.genai.base-url=https://test.api.google.com")
			.run(context -> {
				GoogleGenAiTtsConnectionProperties connectionProperties = context
					.getBean(GoogleGenAiTtsConnectionProperties.class);
				assertThat(connectionProperties.getApiKey()).isEqualTo("test-tts-key");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("https://test.api.google.com");
			});
	}

	@Test
	void ttsPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.options.model=gemini-2.5-pro-preview-tts",
					"spring.ai.google.genai.tts.options.voice=Puck", "spring.ai.google.genai.tts.options.speed=1.2")
			.run(context -> {
				GoogleGenAiTtsProperties ttsProperties = context.getBean(GoogleGenAiTtsProperties.class);
				assertThat(ttsProperties.getOptions().getModel()).isEqualTo("gemini-2.5-pro-preview-tts");
				assertThat(ttsProperties.getOptions().getVoice()).isEqualTo("Puck");
				assertThat(ttsProperties.getOptions().getSpeed()).isEqualTo(1.2);
			});
	}

	@Test
	void ttsDefaultValuesBinding() {
		// Test that defaults are applied when not specified
		this.contextRunner.run(context -> {
			GoogleGenAiTtsProperties ttsProperties = context.getBean(GoogleGenAiTtsProperties.class);
			assertThat(ttsProperties.getOptions().getModel()).isEqualTo("gemini-2.5-flash-preview-tts");
			assertThat(ttsProperties.getOptions().getVoice()).isEqualTo("Kore");
			assertThat(ttsProperties.getOptions().getFormat()).isEqualTo("pcm");
		});
	}

	@Test
	void connectionDefaultBaseUrl() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=test-key").run(context -> {
			GoogleGenAiTtsConnectionProperties connectionProperties = context
				.getBean(GoogleGenAiTtsConnectionProperties.class);
			assertThat(connectionProperties.getBaseUrl()).isEqualTo("https://generativelanguage.googleapis.com");
		});
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiTtsConnectionProperties.class, GoogleGenAiTtsProperties.class })
	static class PropertiesTestConfiguration {

	}

}
