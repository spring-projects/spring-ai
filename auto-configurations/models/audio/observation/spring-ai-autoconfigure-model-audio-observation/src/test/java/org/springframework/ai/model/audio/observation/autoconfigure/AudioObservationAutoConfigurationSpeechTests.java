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

import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationContext;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelTextContentObservationHandler;
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
 * {@code TextToSpeechModelTextContentObservationHandler} part.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class AudioObservationAutoConfigurationSpeechTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AudioObservationAutoConfiguration.class));

	@Test
	void handlerOffByDefaultNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(TextToSpeechModelTextContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void handlerOffByDefaultWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(TextToSpeechModelTextContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void handlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.audio.speech.observations.log-text=true")
			.run(context -> assertThat(context).hasSingleBean(TextToSpeechModelTextContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the text-to-speech text content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void handlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.audio.speech.observations.log-text=true")
			.run(context -> assertThat(context).doesNotHaveBean(TextToSpeechModelTextContentObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class)
				.hasBean("textToSpeechModelTextContentObservationHandler"));
		assertThat(output).contains(
				"You have enabled logging out the text-to-speech text content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void handlerDisabledExplicitly() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.audio.speech.observations.log-text=false")
			.run(context -> assertThat(context).doesNotHaveBean(TextToSpeechModelTextContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customHandlerWinsNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomHandlerConfiguration.class)
			.withPropertyValues("spring.ai.audio.speech.observations.log-text=true")
			.run(context -> assertThat(context).hasSingleBean(TextToSpeechModelTextContentObservationHandler.class)
				.hasBean("customTextToSpeechModelTextContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareHandlerWins() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareHandlerConfiguration.class)
			.withPropertyValues("spring.ai.audio.speech.observations.log-text=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(TextToSpeechModelTextContentObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("textToSpeechModelTextContentObservationHandler");
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
		TextToSpeechModelTextContentObservationHandler customTextToSpeechModelTextContentObservationHandler() {
			return new TextToSpeechModelTextContentObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareHandlerConfiguration {

		static TracingAwareLoggingObservationHandler<TextToSpeechModelObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new TextToSpeechModelTextContentObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<TextToSpeechModelObservationContext> textToSpeechModelTextContentObservationHandler() {
			return handlerInstance;
		}

	}

}
