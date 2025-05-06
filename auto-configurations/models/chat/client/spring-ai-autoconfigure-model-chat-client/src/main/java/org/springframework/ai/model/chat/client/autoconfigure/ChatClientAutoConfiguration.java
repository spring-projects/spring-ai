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

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientPromptContentObservationHandler;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link ChatClient}.
 * <p>
 * This will produce a {@link ChatClient.Builder ChatClient.Builder} bean with the
 * {@code prototype} scope, meaning each injection point will receive a newly cloned
 * instance of the builder.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Josh Long
 * @author Arjen Poutsma
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = { "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration" })
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(ChatClientBuilderProperties.class)
@ConditionalOnProperty(prefix = ChatClientBuilderProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class ChatClientAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ChatClientAutoConfiguration.class);

	private static void logPromptContentWarning() {
		logger.warn(
				"You have enabled logging out the ChatClient prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Bean
	@ConditionalOnMissingBean
	ChatClientBuilderConfigurer chatClientBuilderConfigurer(ObjectProvider<ChatClientCustomizer> customizerProvider) {
		ChatClientBuilderConfigurer configurer = new ChatClientBuilderConfigurer();
		configurer.setChatClientCustomizers(customizerProvider.orderedStream().toList());
		return configurer;
	}

	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	ChatClient.Builder chatClientBuilder(ChatClientBuilderConfigurer chatClientBuilderConfigurer, ChatModel chatModel,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatClientObservationConvention> observationConvention) {

		ChatClient.Builder builder = ChatClient.builder(chatModel,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				observationConvention.getIfUnique(() -> null));
		return chatClientBuilderConfigurer.configure(builder);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Tracer.class)
	@ConditionalOnBean(Tracer.class)
	static class TracerPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean(value = ChatClientPromptContentObservationHandler.class,
				name = "chatClientPromptContentObservationHandler")
		@ConditionalOnProperty(prefix = ChatClientBuilderProperties.CONFIG_PREFIX + ".observations",
				name = "log-prompt", havingValue = "true")
		TracingAwareLoggingObservationHandler<ChatClientObservationContext> chatClientPromptContentObservationHandler(
				Tracer tracer) {
			logPromptContentWarning();
			return new TracingAwareLoggingObservationHandler<>(new ChatClientPromptContentObservationHandler(), tracer);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
	static class TracerNotPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = ChatClientBuilderProperties.CONFIG_PREFIX + ".observations",
				name = "log-prompt", havingValue = "true")
		ChatClientPromptContentObservationHandler chatClientPromptContentObservationHandler() {
			logPromptContentWarning();
			return new ChatClientPromptContentObservationHandler();
		}

	}

}
