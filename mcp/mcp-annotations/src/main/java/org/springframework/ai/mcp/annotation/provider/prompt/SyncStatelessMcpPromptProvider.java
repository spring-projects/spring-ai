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

package org.springframework.ai.mcp.annotation.provider.prompt;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.adapter.PromptAdapter;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.prompt.SyncStatelessMcpPromptMethodCallback;

/**
 * Provider for synchronous stateless MCP prompt methods.
 *
 * This provider creates prompt specifications for methods annotated with
 * {@link McpPrompt} that are designed to work in a stateless manner using
 * {@link McpTransportContext}.
 *
 * @author Christian Tzolov
 */
public class SyncStatelessMcpPromptProvider {

	private static final Logger logger = LoggerFactory.getLogger(SyncStatelessMcpPromptProvider.class);

	private final List<Object> promptObjects;

	/**
	 * Create a new SyncStatelessMcpPromptProvider.
	 * @param promptObjects the objects containing methods annotated with
	 * {@link McpPrompt}
	 */
	public SyncStatelessMcpPromptProvider(List<Object> promptObjects) {
		Assert.notNull(promptObjects, "promptObjects cannot be null");
		this.promptObjects = promptObjects;
	}

	/**
	 * Get the stateless prompt specifications.
	 * @return the list of stateless prompt specifications
	 */
	public List<SyncPromptSpecification> getPromptSpecifications() {

		List<SyncPromptSpecification> promptSpecs = this.promptObjects.stream()
			.map(promptObject -> Stream.of(doGetClassMethods(promptObject))
				.filter(method -> method.isAnnotationPresent(McpPrompt.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.filter(McpPredicates.filterMethodWithBidirectionalParameters())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpPromptMethod -> {
					var promptAnnotation = mcpPromptMethod.getAnnotation(McpPrompt.class);
					var mcpPrompt = PromptAdapter.asPrompt(promptAnnotation, mcpPromptMethod);

					BiFunction<McpTransportContext, GetPromptRequest, GetPromptResult> methodCallback = SyncStatelessMcpPromptMethodCallback
						.builder()
						.method(mcpPromptMethod)
						.bean(promptObject)
						.prompt(mcpPrompt)
						.build();

					return new SyncPromptSpecification(mcpPrompt, methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		if (promptSpecs.isEmpty()) {
			logger.warn("No prompt methods found in the provided prompt objects: {}", this.promptObjects);
		}

		return promptSpecs;
	}

	/**
	 * Returns the methods of the given bean class.
	 * @param bean the bean instance
	 * @return the methods of the bean class
	 */
	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}
