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

package org.springframework.ai.model.tool.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.ToolCallingContentObservationFilter;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration for common tool calling features of {@link ChatModel}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties(ToolCallingProperties.class)
public class ToolCallingAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ToolCallingAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext,
			List<ToolCallback> toolCallbacks, List<ToolCallbackProvider> tcbProviders) {

		List<ToolCallback> allFunctionAndToolCallbacks = new ArrayList<>(toolCallbacks);
		tcbProviders.stream().map(pr -> List.of(pr.getToolCallbacks())).forEach(allFunctionAndToolCallbacks::addAll);

		var staticToolCallbackResolver = new StaticToolCallbackResolver(allFunctionAndToolCallbacks);

		var springBeanToolCallbackResolver = SpringBeanToolCallbackResolver.builder()
			.applicationContext(applicationContext)
			.build();

		return new DelegatingToolCallbackResolver(List.of(staticToolCallbackResolver, springBeanToolCallbackResolver));
	}

	@Bean
	@ConditionalOnMissingBean
	ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
		return new DefaultToolExecutionExceptionProcessor(false);
	}

	@Bean
	@ConditionalOnMissingBean
	ToolCallingManager toolCallingManager(ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ToolCallingObservationConvention> observationConvention) {
		var toolCallingManager = ToolCallingManager.builder()
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolCallbackResolver(toolCallbackResolver)
			.toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
			.build();

		observationConvention.ifAvailable(toolCallingManager::setObservationConvention);

		return toolCallingManager;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ToolCallingProperties.CONFIG_PREFIX + ".observations", name = "include-content",
			havingValue = "true")
	ToolCallingContentObservationFilter toolCallingContentObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the tool call arguments and result in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ToolCallingContentObservationFilter();
	}

}
