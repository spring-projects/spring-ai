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

package org.springframework.ai.model.transcription.observation.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionPromptContentObservationHandler;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link TranscriptionObservationAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
@ExtendWith(OutputCaptureExtension.class)
class TranscriptionObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TranscriptionObservationAutoConfiguration.class));

	@Test
	void transcriptionModelPromptContentHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(AudioTranscriptionPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void transcriptionModelPromptContentHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(AudioTranscriptionPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void transcriptionModelPromptContentHandlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(AudioTranscriptionPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the transcription prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void transcriptionModelPromptContentHandlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=true")
			.run(context -> assertThat(context).doesNotHaveBean(AudioTranscriptionPromptContentObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the transcription prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void transcriptionModelPromptContentHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(AudioTranscriptionPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void transcriptionModelPromptContentHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(AudioTranscriptionPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTranscriptionModelPromptContentObservationHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomAudioTranscriptionPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(AudioTranscriptionPromptContentObservationHandler.class)
				.hasBean("customTranscriptionModelPromptContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));

	}

	@Test
	void customTranscriptionModelPromptContentObservationHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomAudioTranscriptionPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(AudioTranscriptionPromptContentObservationHandler.class)
				.hasBean("customTranscriptionModelPromptContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareLoggingObservationHandler() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareLoggingObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.transcription.observations.log-prompt=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(AudioTranscriptionPromptContentObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("transcriptionModelPromptContentObservationHandler");
				assertThat(context.getBean(TracingAwareLoggingObservationHandler.class))
					.isSameAs(CustomTracingAwareLoggingObservationHandlerConfiguration.handlerInstance);
			});

	}

	@Configuration(proxyBeanMethods = false)
	static class TracerConfiguration {

		@Bean
		Tracer tracer() {
			return mock(Tracer.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomAudioTranscriptionPromptContentObservationHandlerConfiguration {

		@Bean
		AudioTranscriptionPromptContentObservationHandler customTranscriptionModelPromptContentObservationHandler() {
			return new AudioTranscriptionPromptContentObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareLoggingObservationHandlerConfiguration {

		static TracingAwareLoggingObservationHandler<AudioTranscriptionModelObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new AudioTranscriptionPromptContentObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<AudioTranscriptionModelObservationContext> transcriptionModelPromptContentObservationHandler() {
			return handlerInstance;
		}

	}

}
