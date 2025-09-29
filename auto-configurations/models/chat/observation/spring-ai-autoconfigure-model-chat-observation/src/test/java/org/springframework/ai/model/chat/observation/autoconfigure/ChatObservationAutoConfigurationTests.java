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

package org.springframework.ai.model.chat.observation.autoconfigure;

import java.util.List;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.observation.ChatModelCompletionObservationHandler;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelPromptContentObservationHandler;
import org.springframework.ai.model.observation.ErrorLoggingObservationHandler;
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
 * Unit tests for {@link ChatObservationAutoConfiguration}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatObservationAutoConfiguration.class));

	@Test
	void meterObservationHandlerEnabled() {
		this.contextRunner.withBean(CompositeMeterRegistry.class)
			.run(context -> assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class));
	}

	@Test
	void meterObservationHandlerDisabled() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(ChatModelMeterObservationHandler.class));
	}

	@Test
	void handlersNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void handlersWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void promptContentHandlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void promptContentHandlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-prompt=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void promptContentHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void promptContentHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void completionHandlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.log-completion=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.hasSingleBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the completion content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void completionHandlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-completion=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the completion content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void completionHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.log-completion=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void completionHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-completion=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void errorLoggingHandlerEnabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.include-error-logging=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void errorLoggingHandlerEnabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.include-error-logging=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.hasSingleBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void errorLoggingHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.include-error-logging=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void errorLoggingHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.include-error-logging=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void customChatModelPromptContentObservationHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomChatModelPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatModelPromptContentObservationHandler.class)
				.hasBean("customChatModelPromptContentObservationHandler")
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void customChatModelPromptContentObservationHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomChatModelPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatModelPromptContentObservationHandler.class)
				.hasBean("customChatModelPromptContentObservationHandler")
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareLoggingObservationHandlerForChatModelPromptContent() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(
					CustomTracingAwareLoggingObservationHandlerForChatModelPromptContentConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-prompt=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
					.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("chatModelPromptContentObservationHandler")
					.doesNotHaveBean(ErrorLoggingObservationHandler.class);
				assertThat(context.getBean(TracingAwareLoggingObservationHandler.class)).isSameAs(
						CustomTracingAwareLoggingObservationHandlerForChatModelPromptContentConfiguration.handlerInstance);
			});
	}

	@Test
	void customChatModelCompletionObservationHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomChatModelCompletionObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-completion=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.hasSingleBean(ChatModelCompletionObservationHandler.class)
				.hasBean("customChatModelCompletionObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void customChatModelCompletionObservationHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomChatModelCompletionObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-completion=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.hasSingleBean(ChatModelCompletionObservationHandler.class)
				.hasBean("customChatModelCompletionObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.doesNotHaveBean(ErrorLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareLoggingObservationHandlerForChatModelCompletion() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareLoggingObservationHandlerForChatModelCompletionConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.log-completion=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
					.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("chatModelCompletionObservationHandler")
					.doesNotHaveBean(ErrorLoggingObservationHandler.class);
				assertThat(context.getBean(TracingAwareLoggingObservationHandler.class)).isSameAs(
						CustomTracingAwareLoggingObservationHandlerForChatModelCompletionConfiguration.handlerInstance);
			});
	}

	@Test
	void customErrorLoggingObservationHandler() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomErrorLoggingObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.observations.include-error-logging=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class)
				.doesNotHaveBean(ChatModelCompletionObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
				.hasSingleBean(ErrorLoggingObservationHandler.class)
				.hasBean("customErrorLoggingObservationHandler"));
	}

	@Configuration(proxyBeanMethods = false)
	static class TracerConfiguration {

		@Bean
		Tracer tracer() {
			return mock(Tracer.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomChatModelPromptContentObservationHandlerConfiguration {

		@Bean
		ChatModelPromptContentObservationHandler customChatModelPromptContentObservationHandler() {
			return new ChatModelPromptContentObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareLoggingObservationHandlerForChatModelPromptContentConfiguration {

		static TracingAwareLoggingObservationHandler<ChatModelObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new ChatModelPromptContentObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<ChatModelObservationContext> chatModelPromptContentObservationHandler() {
			return handlerInstance;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomChatModelCompletionObservationHandlerConfiguration {

		@Bean
		ChatModelCompletionObservationHandler customChatModelCompletionObservationHandler() {
			return new ChatModelCompletionObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareLoggingObservationHandlerForChatModelCompletionConfiguration {

		static TracingAwareLoggingObservationHandler<ChatModelObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new ChatModelCompletionObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<ChatModelObservationContext> chatModelCompletionObservationHandler() {
			return handlerInstance;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomErrorLoggingObservationHandlerConfiguration {

		@Bean
		ErrorLoggingObservationHandler customErrorLoggingObservationHandler(Tracer tracer) {
			return new ErrorLoggingObservationHandler(tracer, List.of(ChatClientObservationContext.class));
		}

	}

}
