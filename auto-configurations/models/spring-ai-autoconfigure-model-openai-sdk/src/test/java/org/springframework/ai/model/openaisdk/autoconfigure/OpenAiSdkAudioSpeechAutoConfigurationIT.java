/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.model.openaisdk.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechModel;
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAiSdkAudioSpeechAutoConfiguration.
 *
 * @author Ilayaperumal Gopinathan
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiSdkAudioSpeechAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioSpeechAutoConfiguration.class))
		.withPropertyValues("spring.ai.model.audio.speech=openai-sdk",
				"spring.ai.openai-sdk.api-key=" + System.getenv("OPENAI_API_KEY"));

	@Test
	void autoConfigurationEnabled() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(OpenAiSdkAudioSpeechModel.class);
			OpenAiSdkAudioSpeechModel model = context.getBean(OpenAiSdkAudioSpeechModel.class);
			assertThat(model).isNotNull();
			assertThat(model.getDefaultOptions()).isNotNull();
		});
	}

	@Test
	void autoConfigurationDisabled() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioSpeechAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.speech=other")
			.run(context -> assertThat(context).doesNotHaveBean(OpenAiSdkAudioSpeechModel.class));
	}

	@Test
	void defaultPropertiesApplied() {
		this.contextRunner.run(context -> {
			OpenAiSdkAudioSpeechModel model = context.getBean(OpenAiSdkAudioSpeechModel.class);
			OpenAiSdkAudioSpeechOptions options = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();
			assertThat(options.getModel()).isEqualTo("gpt-4o-mini-tts");
			assertThat(options.getVoice()).isEqualTo("alloy");
			assertThat(options.getResponseFormat()).isEqualTo("mp3");
			assertThat(options.getSpeed()).isEqualTo(1.0);
		});
	}

	@Test
	void customPropertiesApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.audio.speech.options.model=tts-1-hd",
					"spring.ai.openai-sdk.audio.speech.options.voice=nova",
					"spring.ai.openai-sdk.audio.speech.options.response-format=opus",
					"spring.ai.openai-sdk.audio.speech.options.speed=1.5")
			.run(context -> {
				OpenAiSdkAudioSpeechModel model = context.getBean(OpenAiSdkAudioSpeechModel.class);
				OpenAiSdkAudioSpeechOptions options = (OpenAiSdkAudioSpeechOptions) model.getDefaultOptions();
				assertThat(options.getModel()).isEqualTo("tts-1-hd");
				assertThat(options.getVoice()).isEqualTo("nova");
				assertThat(options.getResponseFormat()).isEqualTo("opus");
				assertThat(options.getSpeed()).isEqualTo(1.5);
			});
	}

}
