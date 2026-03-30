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

package org.springframework.ai.model.openaisdk.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openaisdk.OpenAiSdkAudioTranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiSdkAudioTranscriptionProperties}.
 *
 * @author Ilayaperumal Gopinathan
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 */
class OpenAiSdkAudioTranscriptionPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void transcriptionPropertiesBindCorrectly() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.audio.transcription=openai-sdk",
					"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL", "spring.ai.openai-sdk.api-key=abc123",
					"spring.ai.openai-sdk.audio.transcription.options.model=whisper-1",
					"spring.ai.openai-sdk.audio.transcription.options.language=en")
			.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(OpenAiSdkAudioTranscriptionProperties.class);
				OpenAiSdkAudioTranscriptionProperties properties = context
					.getBean(OpenAiSdkAudioTranscriptionProperties.class);
				assertThat(properties.getOptions().getModel()).isEqualTo("whisper-1");
				assertThat(properties.getOptions().getLanguage()).isEqualTo("en");
			});
	}

	@Test
	void transcriptionBeanCreatedWhenPropertySet() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.audio.transcription=openai-sdk",
					"spring.ai.openai-sdk.api-key=test-key")
			.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioTranscriptionAutoConfiguration.class))
			.run(context -> assertThat(context).hasSingleBean(OpenAiSdkAudioTranscriptionModel.class));
	}

}
