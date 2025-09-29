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

package org.springframework.ai.model.chat.client.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientPromptContentObservationHandler;
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
 * Unit tests for {@link ChatClientAutoConfiguration} observability support.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatClientObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class));

	@Test
	void promptContentHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(ChatClientPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void promptContentHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(ChatClientPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void promptContentHandlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatClientPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the ChatClient prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void promptContentHandlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=true")
			.run(context -> assertThat(context).doesNotHaveBean(ChatClientPromptContentObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out the ChatClient prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void promptContentHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatClientPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void promptContentHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatClientPromptContentObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customChatClientPromptContentObservationHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomChatClientPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatClientPromptContentObservationHandler.class)
				.hasBean("customChatClientPromptContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customChatClientPromptContentObservationHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomChatClientPromptContentObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatClientPromptContentObservationHandler.class)
				.hasBean("customChatClientPromptContentObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareLoggingObservationHandler() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareLoggingObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.chat.client.observations.log-prompt=true")
			.run(context -> {
				assertThat(context).hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("chatClientPromptContentObservationHandler")
					.doesNotHaveBean(ChatClientPromptContentObservationHandler.class);
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
	static class CustomChatClientPromptContentObservationHandlerConfiguration {

		@Bean
		ChatClientPromptContentObservationHandler customChatClientPromptContentObservationHandler() {
			return new ChatClientPromptContentObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareLoggingObservationHandlerConfiguration {

		static TracingAwareLoggingObservationHandler<ChatClientObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new ChatClientPromptContentObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<ChatClientObservationContext> chatClientPromptContentObservationHandler() {
			return handlerInstance;
		}

	}

}
