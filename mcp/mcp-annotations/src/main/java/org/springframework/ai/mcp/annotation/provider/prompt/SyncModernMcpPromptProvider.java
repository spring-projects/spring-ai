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

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.server.feature.McpSyncPrompt;
import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.adapter.PromptAdapter;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.prompt.SyncModernMcpPromptMethodCallback;
import org.springframework.ai.mcp.modern.McpSchemaConverter;

/**
 * Creates {@link McpSyncPrompt}s for a 2026-07-28 ("modern") MCP server from methods
 * annotated with {@link McpPrompt}. Each method is served by a
 * {@link SyncModernMcpPromptMethodCallback}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SyncModernMcpPromptProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpPromptProvider.class);

	private final List<Object> promptObjects;

	private @Nullable McpJsonMapper jsonMapper;

	public SyncModernMcpPromptProvider(List<Object> promptObjects) {
		Assert.notNull(promptObjects, "promptObjects cannot be null");
		this.promptObjects = promptObjects;
	}

	/**
	 * Sets the mapper the prompt methods convert their MRTR input responses with,
	 * normally the server's. Unset, the SDK default is used.
	 */
	public void setJsonMapper(@Nullable McpJsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public List<McpSyncPrompt> getPrompts() {
		List<McpSyncPrompt> prompts = this.promptObjects.stream()
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
						.jsonMapper(this.jsonMapper)
						.build();
					return McpSyncPrompt.of(McpSchemaConverter.toModern(prompt), callback);
				}))
			.toList();

		if (prompts.isEmpty() && logger.isWarnEnabled()) {
			logger.warn("No prompt methods found in the provided prompt objects: " + this.promptObjects);
		}
		return prompts;
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}
