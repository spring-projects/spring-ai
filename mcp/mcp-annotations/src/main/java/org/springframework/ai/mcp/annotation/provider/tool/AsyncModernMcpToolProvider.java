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

package org.springframework.ai.mcp.annotation.provider.tool;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.modern.server.McpAsyncNotifier;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.tool.AsyncModernMcpToolMethodCallback;

/**
 * The asynchronous twin of {@link SyncModernMcpToolProvider}: creates
 * {@link McpAsyncTool}s for a 2026-07-28 ("modern") MCP server from methods annotated
 * with {@link McpTool}. Reactive and plain return types are both served; a plain method
 * runs through {@code McpModernAsyncUtils.fromBlocking}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class AsyncModernMcpToolProvider extends AbstractMcpToolProvider {

	private static final Log logger = LogFactory.getLog(AsyncModernMcpToolProvider.class);

	public AsyncModernMcpToolProvider(List<Object> toolObjects) {
		super(toolObjects);
	}

	public List<McpAsyncTool> getTools() {
		List<McpAsyncTool> tools = this.toolObjects.stream()
			.map(toolObject -> Stream.of(this.doGetClassMethods(toolObject))
				.filter(method -> method.isAnnotationPresent(McpTool.class))
				.filter(ModernMethodSupport.filterLegacyContextMethods(McpAsyncNotifier.class))
				.sorted(Comparator.comparing(Method::getName))
				.map(mcpToolMethod -> toTool(toolObject, mcpToolMethod))
				.toList())
			.flatMap(List::stream)
			.toList();
		if (tools.isEmpty() && logger.isWarnEnabled()) {
			logger.warn("No tool methods found in the provided tool objects: " + this.toolObjects);
		}
		return tools;
	}

	private McpAsyncTool toTool(Object toolObject, Method mcpToolMethod) {
		var toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);
		ModernToolDefinitions.Definition definition = ModernToolDefinitions.define(mcpToolMethod, toolJavaAnnotation,
				this.getJsonMapper());
		return McpAsyncTool.of(definition.tool(),
				ModernMethodSupport.requiredCapabilities(mcpToolMethod, toolJavaAnnotation.requiredCapabilities()),
				new AsyncModernMcpToolMethodCallback(definition.returnMode(), mcpToolMethod, toolObject,
						this.getJsonMapper()));
	}

}
