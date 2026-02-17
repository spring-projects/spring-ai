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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.google.genai.tts.GeminiTtsModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@link GoogleGenAiTtsAutoConfiguration}.
 *
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
public class GoogleGenAiTtsAutoConfigurationIT {

	private static final org.apache.commons.logging.Log logger = org.apache.commons.logging.LogFactory
		.getLog(GoogleGenAiTtsAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.google.genai.api-key=" + System.getenv("GOOGLE_API_KEY"),
				"spring.ai.model.audio.speech=google-genai")
		.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiTtsAutoConfiguration.class));

	@Test
	void ttsModelBeanCreation() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(GeminiTtsModel.class);
			GeminiTtsModel ttsModel = context.getBean(GeminiTtsModel.class);
			assertThat(ttsModel).isNotNull();
		});
	}

	@Test
	void ttsModelApiCall() {
		this.contextRunner.run(context -> {
			GeminiTtsModel ttsModel = context.getBean(GeminiTtsModel.class);
			byte[] response = ttsModel.call("Hello");

			assertThat(response).isNotNull();
			assertThat(response).isNotEmpty();
			// PCM audio should be substantial (24kHz * 2 bytes/sample * ~1 second)
			assertThat(response.length).isGreaterThan(1000);

			logger.debug("PCM audio response size: " + response.length + " bytes");
		});
	}

	@Test
	void ttsModelWithCustomVoice() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.options.voice=Puck",
					"spring.ai.google.genai.tts.options.model=gemini-2.5-flash-preview-tts")
			.run(context -> {
				GeminiTtsModel ttsModel = context.getBean(GeminiTtsModel.class);
				byte[] response = ttsModel.call("Testing custom voice");

				assertThat(response).isNotNull();
				assertThat(response).isNotEmpty();
				assertThat(response.length).isGreaterThan(1000);
			});
	}

}
