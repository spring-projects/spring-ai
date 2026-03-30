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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openaisdk.OpenAiSdkAudioTranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiSdkAudioTranscriptionAutoConfiguration}.
 *
 * @author Ilayaperumal Gopinathan
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 2.0.0
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiSdkAudioTranscriptionAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiSdkAudioTranscriptionAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
			"spring.ai.openai-sdk.api-key=" + System.getenv("OPENAI_API_KEY"),
			"spring.ai.model.audio.transcription=openai-sdk");

	@Test
	void transcribe() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				OpenAiSdkAudioTranscriptionModel transcriptionModel = context
					.getBean(OpenAiSdkAudioTranscriptionModel.class);
				AudioTranscriptionResponse response = transcriptionModel
					.call(new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac")));
				assertThat(response.getResults()).hasSize(1);
				assertThat(response.getResult().getOutput()).isNotBlank();
				logger.info("Transcription: " + response.getResult().getOutput());
			});
	}

	@Test
	void transcriptionActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
					"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL", "spring.ai.model.audio.transcription=none")
			.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkAudioTranscriptionProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkAudioTranscriptionModel.class)).isEmpty();
			});

		new ApplicationContextRunner().withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL", "spring.ai.model.audio.transcription=openai-sdk")
			.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkAudioTranscriptionModel.class)).isNotEmpty();
			});
	}

	@Test
	void transcriptionOptionsTest() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL", "spring.ai.model.audio.transcription=openai-sdk",
				"spring.ai.openai-sdk.audio.transcription.options.model=whisper-1",
				"spring.ai.openai-sdk.audio.transcription.options.language=en",
				"spring.ai.openai-sdk.audio.transcription.options.temperature=0.5")
			.withConfiguration(AutoConfigurations.of(OpenAiSdkAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);
				var transcriptionProperties = context.getBean(OpenAiSdkAudioTranscriptionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(transcriptionProperties.getOptions().getModel()).isEqualTo("whisper-1");
				assertThat(transcriptionProperties.getOptions().getLanguage()).isEqualTo("en");
				assertThat(transcriptionProperties.getOptions().getTemperature()).isEqualTo(0.5f);
			});
	}

}
