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

package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiAudioTranscriptionProperties}.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 */
class OpenAiAudioTranscriptionPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void transcriptionOptionsTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://TEST.BASE.URL",
					"spring.ai.model.audio.transcription=openai",
					"spring.ai.openai.audio.transcription.options.model=whisper-1",
					"spring.ai.openai.audio.transcription.options.language=en",
					"spring.ai.openai.audio.transcription.options.temperature=0.5")
			.withConfiguration(AutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				var commonProperties = context.getBean(OpenAiCommonProperties.class);
				var transcriptionProperties = context.getBean(OpenAiAudioTranscriptionProperties.class);

				assertThat(commonProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(commonProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(transcriptionProperties.getOptions().getModel()).isEqualTo("whisper-1");
				assertThat(transcriptionProperties.getOptions().getLanguage()).isEqualTo("en");
				assertThat(transcriptionProperties.getOptions().getTemperature()).isEqualTo(0.5f);
			});
	}

	@Test
	void transcriptionPropertiesBindCorrectly() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.audio.transcription=openai",
					"spring.ai.openai.base-url=http://TEST.BASE.URL", "spring.ai.openai.api-key=abc123",
					"spring.ai.openai.audio.transcription.options.model=whisper-1",
					"spring.ai.openai.audio.transcription.options.language=en")
			.withConfiguration(AutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(OpenAiAudioTranscriptionProperties.class);
				OpenAiAudioTranscriptionProperties properties = context
					.getBean(OpenAiAudioTranscriptionProperties.class);
				assertThat(properties.getOptions().getModel()).isEqualTo("whisper-1");
				assertThat(properties.getOptions().getLanguage()).isEqualTo("en");
			});
	}

	@Test
	void transcriptionBeanCreatedWhenPropertySet() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.audio.transcription=openai", "spring.ai.openai.api-key=test-key")
			.withConfiguration(AutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> assertThat(context).hasSingleBean(OpenAiAudioTranscriptionModel.class));
	}

	@Test
	void transcriptionActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://TEST.BASE.URL",
					"spring.ai.model.audio.transcription=none")
			.withConfiguration(AutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://TEST.BASE.URL",
					"spring.ai.model.audio.transcription=openai")
			.withConfiguration(AutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
			});
	}

}
