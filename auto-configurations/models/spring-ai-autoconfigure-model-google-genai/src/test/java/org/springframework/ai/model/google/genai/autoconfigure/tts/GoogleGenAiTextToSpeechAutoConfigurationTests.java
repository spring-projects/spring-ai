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

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechConnectionDetails;
import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiTextToSpeechAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTextToSpeechAutoConfigurationTests {

	private static final GoogleGenAiTextToSpeechConnectionDetails CONNECTION_DETAILS = GoogleGenAiTextToSpeechConnectionDetails
		.builder()
		.projectId("test-project")
		.textToSpeechClient(Mockito.mock(TextToSpeechClient.class))
		.build();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(GoogleGenAiTextToSpeechConnectionDetails.class, () -> CONNECTION_DETAILS)
		.withConfiguration(AutoConfigurations.of(GoogleGenAiTextToSpeechAutoConfiguration.class));

	@Test
	void createsTextToSpeechModelByDefault() {
		this.contextRunner
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiTextToSpeechModel.class)).hasSize(1));
	}

	@Test
	void backsOffWhenUserSuppliesOwnModel() {
		this.contextRunner.withUserConfiguration(CustomTextToSpeechModelConfiguration.class).run(context -> {
			assertThat(context.getBeansOfType(GoogleGenAiTextToSpeechModel.class)).hasSize(1);
			assertThat(context.getBean(GoogleGenAiTextToSpeechModel.class))
				.isSameAs(CustomTextToSpeechModelConfiguration.CUSTOM_MODEL);
		});
	}

	@Test
	void disabledWhenAudioSpeechModelIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.model.audio.speech=none")
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiTextToSpeechModel.class)).isEmpty());
	}

	@Test
	void enabledWhenAudioSpeechModelIsGoogleGenAi() {
		this.contextRunner.withPropertyValues("spring.ai.model.audio.speech=google-genai")
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiTextToSpeechModel.class)).hasSize(1));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTextToSpeechModelConfiguration {

		static final GoogleGenAiTextToSpeechModel CUSTOM_MODEL = Mockito.mock(GoogleGenAiTextToSpeechModel.class);

		@Bean
		GoogleGenAiTextToSpeechModel googleGenAiTextToSpeechModel() {
			return CUSTOM_MODEL;
		}

	}

}
