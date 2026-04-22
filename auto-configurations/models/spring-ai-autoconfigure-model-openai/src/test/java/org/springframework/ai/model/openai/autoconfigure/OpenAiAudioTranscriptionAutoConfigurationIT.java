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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiAudioTranscriptionAutoConfiguration}.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAudioTranscriptionAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiAudioTranscriptionAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
			"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
			"spring.ai.model.audio.transcription=openai");

	@Test
	void transcribe() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				OpenAiAudioTranscriptionModel transcriptionModel = context.getBean(OpenAiAudioTranscriptionModel.class);
				AudioTranscriptionResponse response = transcriptionModel
					.call(new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac")));
				assertThat(response.getResults()).hasSize(1);
				assertThat(response.getResult().getOutput()).isNotBlank();
				logger.info("Transcription: " + response.getResult().getOutput());
			});
	}

}
