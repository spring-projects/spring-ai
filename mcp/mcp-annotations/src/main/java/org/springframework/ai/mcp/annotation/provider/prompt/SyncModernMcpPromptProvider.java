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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.adapter.PromptAdapter;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.prompt.SyncModernMcpPromptMethodCallback;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncPromptSpecification;

/**
 * Creates prompt specifications for a 2026-07-28 ("modern") MCP server from methods
 * annotated with {@link McpPrompt}. Each method is served by a
 * {@link SyncModernMcpPromptMethodCallback}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public class SyncModernMcpPromptProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpPromptProvider.class);

	private final List<Object> promptObjects;

	public SyncModernMcpPromptProvider(List<Object> promptObjects) {
		Assert.notNull(promptObjects, "promptObjects cannot be null");
		this.promptObjects = promptObjects;
	}

	public List<SyncPromptSpecification> getModernPromptSpecifications() {
		List<SyncPromptSpecification> specifications = this.promptObjects.stream()
			.flatMap(bean -> Stream.of(this.doGetClassMethods(bean))
				.filter(method -> method.isAnnotationPresent(McpPrompt.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.filter(ModernMethodSupport.filterLegacyContextMethods())
				.sorted(Comparator.comparing(Method::getName))
				.map(method -> {
					var prompt = PromptAdapter.asPrompt(method.getAnnotation(McpPrompt.class), method);
					SyncModernMcpPromptMethodCallback callback = SyncModernMcpPromptMethodCallback.builder()
						.method(method)
						.bean(bean)
						.prompt(prompt)
						.build();
					return new SyncPromptSpecification(McpSchemaConverter.toModern(prompt), callback);
				}))
			.toList();

		if (specifications.isEmpty() && logger.isWarnEnabled()) {
			logger.warn("No prompt methods found in the provided prompt objects: " + this.promptObjects);
		}
		return specifications;
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}
