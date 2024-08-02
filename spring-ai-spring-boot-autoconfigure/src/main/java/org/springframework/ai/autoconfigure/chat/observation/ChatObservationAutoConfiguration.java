/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.chat.observation;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelCompletionObservationFilter;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelPromptContentObservationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI chat model observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration.class")
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties({ ChatObservationProperties.class })
public class ChatObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ChatObservationAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MeterRegistry.class)
	ChatModelMeterObservationHandler chatModelMeterObservationHandler(ObjectProvider<MeterRegistry> meterRegistry) {
		return new ChatModelMeterObservationHandler(meterRegistry.getObject());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "include-prompt",
			havingValue = "true")
	ChatModelPromptContentObservationFilter chatModelPromptObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the prompt content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ChatModelPromptContentObservationFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ChatObservationProperties.CONFIG_PREFIX, name = "include-completion",
			havingValue = "true")
	ChatModelCompletionObservationFilter chatModelCompletionObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the completion content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ChatModelCompletionObservationFilter();
	}

}
