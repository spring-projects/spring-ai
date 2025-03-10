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

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Auto-configuration for common tool calling features of {@link ChatModel}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
public class ToolCallingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext,
			List<FunctionCallback> functionCallbacks, List<ToolCallbackProvider> tcbProviders) {

		List<FunctionCallback> allFunctionAndToolCallbacks = new ArrayList<>(functionCallbacks);
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
			ObjectProvider<ObservationRegistry> observationRegistry) {
		return ToolCallingManager.builder()
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolCallbackResolver(toolCallbackResolver)
			.toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
			.build();
	}

}
