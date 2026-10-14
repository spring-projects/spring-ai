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

package org.springframework.ai.model.google.genai.autoconfigure.transcription;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Google GenAI Transcription properties binding.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesTestConfiguration.class);

	@Test
	void connectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.api-key=test-key",
					"spring.ai.google.genai.transcription.project-id=test-project",
					"spring.ai.google.genai.transcription.location=us-central1",
					"spring.ai.google.genai.transcription.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiTranscriptionConnectionProperties props = context
					.getBean(GoogleGenAiTranscriptionConnectionProperties.class);
				assertThat(props.getApiKey()).isEqualTo("test-key");
				assertThat(props.getProjectId()).isEqualTo("test-project");
				assertThat(props.getLocation()).isEqualTo("us-central1");
				assertThat(props.getCredentialsUri()).isNotNull();
			});
	}

	@Test
	void connectionPropertiesDefaults() {
		this.contextRunner.run(context -> {
			GoogleGenAiTranscriptionConnectionProperties props = context
				.getBean(GoogleGenAiTranscriptionConnectionProperties.class);
			assertThat(props.getApiKey()).isNull();
			assertThat(props.getProjectId()).isNull();
			assertThat(props.getLocation()).isEqualTo("global");
			assertThat(props.getCredentialsUri()).isNull();
		});
	}

	@Test
	void optionsPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.model=gemini-2.5-flash",
					"spring.ai.google.genai.transcription.prompt=Transcribe this audio",
					"spring.ai.google.genai.transcription.language=en-US",
					"spring.ai.google.genai.transcription.temperature=0.3",
					"spring.ai.google.genai.transcription.top-p=0.9", "spring.ai.google.genai.transcription.top-k=40",
					"spring.ai.google.genai.transcription.candidate-count=2",
					"spring.ai.google.genai.transcription.max-output-tokens=1024",
					"spring.ai.google.genai.transcription.stop-sequences=END,STOP",
					"spring.ai.google.genai.transcription.response-mime-type=text/plain",
					"spring.ai.google.genai.transcription.presence-penalty=0.5",
					"spring.ai.google.genai.transcription.frequency-penalty=0.4",
					"spring.ai.google.genai.transcription.seed=42")
			.run(context -> {
				GoogleGenAiTranscriptionProperties props = context.getBean(GoogleGenAiTranscriptionProperties.class);
				GoogleGenAiAudioTranscriptionOptions options = props.toOptions();
				assertThat(options.getModel()).isEqualTo("gemini-2.5-flash");
				assertThat(options.getPrompt()).isEqualTo("Transcribe this audio");
				assertThat(options.getLanguage()).isEqualTo("en-US");
				assertThat(options.getTemperature()).isEqualTo(0.3f);
				assertThat(options.getTopP()).isEqualTo(0.9f);
				assertThat(options.getTopK()).isEqualTo(40.0f);
				assertThat(options.getCandidateCount()).isEqualTo(2);
				assertThat(options.getMaxOutputTokens()).isEqualTo(1024);
				assertThat(options.getStopSequences()).containsExactly("END", "STOP");
				assertThat(options.getResponseMimeType()).isEqualTo("text/plain");
				assertThat(options.getPresencePenalty()).isEqualTo(0.5f);
				assertThat(options.getFrequencyPenalty()).isEqualTo(0.4f);
				assertThat(options.getSeed()).isEqualTo(42);
			});
	}

	@Test
	void defaultOptionsBinding() {
		this.contextRunner.run(context -> {
			GoogleGenAiTranscriptionProperties props = context.getBean(GoogleGenAiTranscriptionProperties.class);
			GoogleGenAiAudioTranscriptionOptions options = props.toOptions();
			assertThat(options.getModel()).isEqualTo(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME);
			assertThat(options.getPrompt()).isNull();
			assertThat(options.getLanguage()).isNull();
			assertThat(options.getTemperature()).isNull();
			assertThat(options.getTopP()).isNull();
			assertThat(options.getTopK()).isNull();
			assertThat(options.getCandidateCount()).isNull();
			assertThat(options.getMaxOutputTokens()).isNull();
			assertThat(options.getStopSequences()).isNull();
			assertThat(options.getResponseMimeType()).isNull();
			assertThat(options.getPresencePenalty()).isNull();
			assertThat(options.getFrequencyPenalty()).isNull();
			assertThat(options.getSeed()).isNull();
		});
	}

	@Test
	void connectionPropertiesGettersAndSetters() {
		GoogleGenAiTranscriptionConnectionProperties props = new GoogleGenAiTranscriptionConnectionProperties();
		props.setApiKey("api-key");
		props.setProjectId("project-id");
		props.setLocation("location");
		ClassPathResource credentialsUri = new ClassPathResource("fake-credentials.json");
		props.setCredentialsUri(credentialsUri);

		assertThat(props.getApiKey()).isEqualTo("api-key");
		assertThat(props.getProjectId()).isEqualTo("project-id");
		assertThat(props.getLocation()).isEqualTo("location");
		assertThat(props.getCredentialsUri()).isEqualTo(credentialsUri);
	}

	@Test
	void transcriptionPropertiesGettersAndSetters() {
		GoogleGenAiTranscriptionProperties props = new GoogleGenAiTranscriptionProperties();
		props.setModel("model");
		props.setPrompt("prompt");
		props.setLanguage("en-US");
		props.setTemperature(0.3f);
		props.setTopP(0.9f);
		props.setTopK(40f);
		props.setCandidateCount(2);
		props.setMaxOutputTokens(1024);
		props.setStopSequences(List.of("END", "STOP"));
		props.setResponseMimeType("text/plain");
		props.setPresencePenalty(0.5f);
		props.setFrequencyPenalty(0.4f);
		props.setSeed(42);

		assertThat(props.getModel()).isEqualTo("model");
		assertThat(props.getPrompt()).isEqualTo("prompt");
		assertThat(props.getLanguage()).isEqualTo("en-US");
		assertThat(props.getTemperature()).isEqualTo(0.3f);
		assertThat(props.getTopP()).isEqualTo(0.9f);
		assertThat(props.getTopK()).isEqualTo(40f);
		assertThat(props.getCandidateCount()).isEqualTo(2);
		assertThat(props.getMaxOutputTokens()).isEqualTo(1024);
		assertThat(props.getStopSequences()).containsExactly("END", "STOP");
		assertThat(props.getResponseMimeType()).isEqualTo("text/plain");
		assertThat(props.getPresencePenalty()).isEqualTo(0.5f);
		assertThat(props.getFrequencyPenalty()).isEqualTo(0.4f);
		assertThat(props.getSeed()).isEqualTo(42);
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiTranscriptionConnectionProperties.class,
			GoogleGenAiTranscriptionProperties.class })
	static class PropertiesTestConfiguration {

	}

}
