/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.chat.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.client.observation.ChatClientInputContentObservationFilter;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import io.micrometer.observation.ObservationRegistry;

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
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(ChatClientBuilderProperties.class)
@ConditionalOnProperty(prefix = ChatClientBuilderProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class ChatClientAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ChatClientAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	ChatClientBuilderConfigurer chatClientBuilderConfigurer(ObjectProvider<ChatClientCustomizer> customizerProvider) {
		ChatClientBuilderConfigurer configurer = new ChatClientBuilderConfigurer();
		configurer.setChatClientCustomizers(customizerProvider.orderedStream().toList());
		return configurer;
	}

	@Bean
	@Scope("prototype")
	ChatClient.Builder chatClientBuilder(ChatClientBuilderConfigurer chatClientBuilderConfigurer, ChatModel chatModel,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatClientObservationConvention> observationConvention) {

		ChatClient.Builder builder = ChatClient.builder(chatModel,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				observationConvention.getIfUnique(() -> null));
		return chatClientBuilderConfigurer.configure(builder);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ChatClientBuilderProperties.CONFIG_PREFIX + ".observations", name = "include-input",
			havingValue = "true")
	ChatClientInputContentObservationFilter chatClientInputContentObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the input content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ChatClientInputContentObservationFilter();
	}

}
