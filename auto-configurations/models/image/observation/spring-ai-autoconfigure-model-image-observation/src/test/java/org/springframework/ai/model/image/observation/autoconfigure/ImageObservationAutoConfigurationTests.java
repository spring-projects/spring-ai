/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.image.observation.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelPromptContentObservationHandler;
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
 * Unit tests for {@link ImageObservationAutoConfiguration}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ImageObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ImageObservationAutoConfiguration.class));

	@Test
	void imageModelPromptContentHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void imageModelPromptContentHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void imageModelPromptContentHandlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.image.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ImageModelPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the image prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void imageModelPromptContentHandlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.image.observations.log-prompt=true")
			.run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the image prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void imageModelPromptContentHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.image.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void imageModelPromptContentHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.image.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customChatClientPromptContentObservationHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomImageModelPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.image.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ImageModelPromptContentObservationHandler.class)
				.hasBean("customImageModelPromptContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));

	}

	@Test
	void customChatClientPromptContentObservationHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomImageModelPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.image.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ImageModelPromptContentObservationHandler.class)
				.hasBean("customImageModelPromptContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareLoggingObservationHandler() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareLoggingObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.image.observations.log-prompt=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("imageModelPromptContentObservationHandler");
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
	static class CustomImageModelPromptContentObservationHandlerConfiguration {

		@Bean
		ImageModelPromptContentObservationHandler customImageModelPromptContentObservationHandler() {
			return new ImageModelPromptContentObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareLoggingObservationHandlerConfiguration {

		static TracingAwareLoggingObservationHandler<ImageModelObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new ImageModelPromptContentObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<ImageModelObservationContext> imageModelPromptContentObservationHandler() {
			return handlerInstance;
		}

	}

}
