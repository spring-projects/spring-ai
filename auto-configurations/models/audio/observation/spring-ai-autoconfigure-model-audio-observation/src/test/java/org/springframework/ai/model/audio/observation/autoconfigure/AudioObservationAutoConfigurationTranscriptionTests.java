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

package org.springframework.ai.model.audio.observation.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelTranscriptObservationHandler;
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
 * Unit tests for {@link AudioObservationAutoConfiguration}: the
 * {@code TranscriptionModelTranscriptObservationHandler} part.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class AudioObservationAutoConfigurationTranscriptionTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AudioObservationAutoConfiguration.class));

	@Test
	void handlerOffByDefaultNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(TranscriptionModelTranscriptObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void handlerOffByDefaultWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(TranscriptionModelTranscriptObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void handlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.audio.transcription.observations.log-transcript=true")
			.run(context -> assertThat(context).hasSingleBean(TranscriptionModelTranscriptObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the transcription transcript with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void handlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.audio.transcription.observations.log-transcript=true")
			.run(context -> assertThat(context).doesNotHaveBean(TranscriptionModelTranscriptObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class)
				.hasBean("transcriptionModelTranscriptObservationHandler"));
		assertThat(output).contains(
				"You have enabled logging out the transcription transcript with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void handlerDisabledExplicitly() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.audio.transcription.observations.log-transcript=false")
			.run(context -> assertThat(context).doesNotHaveBean(TranscriptionModelTranscriptObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customHandlerWinsNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomHandlerConfiguration.class)
			.withPropertyValues("spring.ai.audio.transcription.observations.log-transcript=true")
			.run(context -> assertThat(context).hasSingleBean(TranscriptionModelTranscriptObservationHandler.class)
				.hasBean("customTranscriptionModelTranscriptObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareHandlerWins() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareHandlerConfiguration.class)
			.withPropertyValues("spring.ai.audio.transcription.observations.log-transcript=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(TranscriptionModelTranscriptObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("transcriptionModelTranscriptObservationHandler");
				assertThat(context.getBean(TracingAwareLoggingObservationHandler.class))
					.isSameAs(CustomTracingAwareHandlerConfiguration.handlerInstance);
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
	static class CustomHandlerConfiguration {

		@Bean
		TranscriptionModelTranscriptObservationHandler customTranscriptionModelTranscriptObservationHandler() {
			return new TranscriptionModelTranscriptObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareHandlerConfiguration {

		static TracingAwareLoggingObservationHandler<TranscriptionModelObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new TranscriptionModelTranscriptObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<TranscriptionModelObservationContext> transcriptionModelTranscriptObservationHandler() {
			return handlerInstance;
		}

	}

}
