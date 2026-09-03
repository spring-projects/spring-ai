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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GoogleGenAiTextToSpeechAutoConfiguration}.
 *
 * <p>
 * Activated via {@code GOOGLE_CLOUD_PROJECT} (Cloud Text-to-Speech uses Application
 * Default Credentials). Tests are skipped when the environment variable is absent.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTextToSpeechAutoConfigurationIT {

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
	void speechWithGeminiTts() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.tts.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.tts.model=gemini-2.5-flash-tts",
					"spring.ai.google.genai.tts.voice-name=Kore", "spring.ai.google.genai.tts.language-code=en-us")
			.withConfiguration(AutoConfigurations.of(GoogleGenAiTextToSpeechAutoConfiguration.class,
					GoogleGenAiTextToSpeechConnectionAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiTextToSpeechModel speechModel = context.getBean(GoogleGenAiTextToSpeechModel.class);
			TextToSpeechResponse response = speechModel
				.call(new TextToSpeechPrompt("Hello from Spring AI and Gemini text to speech."));
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResult().getOutput()).isNotEmpty();
		});
	}

}
