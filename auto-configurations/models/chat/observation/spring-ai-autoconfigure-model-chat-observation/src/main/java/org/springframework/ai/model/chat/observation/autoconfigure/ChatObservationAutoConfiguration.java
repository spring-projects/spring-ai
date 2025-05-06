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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelCompletionObservationHandler;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelPromptContentObservationHandler;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.model.observation.ErrorLoggingObservationHandler;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for Spring AI chat model observations.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = { "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration" })
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties({ ChatObservationProperties.class })
public class ChatObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ChatObservationAutoConfiguration.class);

	private static void logPromptContentWarning() {
		logger.warn(
				"You have enabled logging out the prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	private static void logCompletionWarning() {
		logger.warn(
				"You have enabled logging out the completion content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MeterRegistry.class)
	ChatModelMeterObservationHandler chatModelMeterObservationHandler(ObjectProvider<MeterRegistry> meterRegistry) {
		return new ChatModelMeterObservationHandler(meterRegistry.getObject());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Tracer.class)
	@ConditionalOnBean(Tracer.class)
	static class TracerPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean(value = ChatModelPromptContentObservationHandler.class,
				name = "chatModelPromptContentObservationHandler")
		@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "log-prompt",
				havingValue = "true")
		TracingAwareLoggingObservationHandler<ChatModelObservationContext> chatModelPromptContentObservationHandler(
				Tracer tracer) {
			logPromptContentWarning();
			return new TracingAwareLoggingObservationHandler<>(new ChatModelPromptContentObservationHandler(), tracer);
		}

		@Bean
		@ConditionalOnMissingBean(value = ChatModelCompletionObservationHandler.class,
				name = "chatModelCompletionObservationHandler")
		@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "log-completion",
				havingValue = "true")
		TracingAwareLoggingObservationHandler<ChatModelObservationContext> chatModelCompletionObservationHandler(
				Tracer tracer) {
			logCompletionWarning();
			return new TracingAwareLoggingObservationHandler<>(new ChatModelCompletionObservationHandler(), tracer);
		}

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "include-error-logging",
				havingValue = "true")
		ErrorLoggingObservationHandler errorLoggingObservationHandler(Tracer tracer) {
			return new ErrorLoggingObservationHandler(tracer,
					List.of(EmbeddingModelObservationContext.class, ImageModelObservationContext.class,
							ChatModelObservationContext.class, ChatClientObservationContext.class,
							AdvisorObservationContext.class));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
	static class TracerNotPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "log-prompt",
				havingValue = "true")
		ChatModelPromptContentObservationHandler chatModelPromptContentObservationHandler() {
			logPromptContentWarning();
			return new ChatModelPromptContentObservationHandler();
		}

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "log-completion",
				havingValue = "true")
		ChatModelCompletionObservationHandler chatModelCompletionObservationHandler() {
			logCompletionWarning();
			return new ChatModelCompletionObservationHandler();
		}

	}

}
