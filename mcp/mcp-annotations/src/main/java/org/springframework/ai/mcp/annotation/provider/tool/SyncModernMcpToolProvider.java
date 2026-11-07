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

import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.tool.SyncModernMcpToolMethodCallback;

/**
 * Creates {@link McpSyncTool}s for a 2026-07-28 ("modern") MCP server from methods
 * annotated with {@link McpTool}. Each tool requires the client capabilities listed in
 * {@link McpTool#requiredCapabilities()}, which the server checks before the method runs.
 * Input requested beyond them is checked when the method returns it.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see AsyncModernMcpToolProvider
 */
public class SyncModernMcpToolProvider extends AbstractMcpToolProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpToolProvider.class);

	public SyncModernMcpToolProvider(List<Object> toolObjects) {
		super(toolObjects);
	}

	public List<McpSyncTool> getTools() {
		List<McpSyncTool> tools = this.toolObjects.stream()
			.map(toolObject -> Stream.of(this.doGetClassMethods(toolObject))
				.filter(method -> method.isAnnotationPresent(McpTool.class))
				.filter(ModernMethodSupport.filterAsyncReturnTypeMethods())
				// Doesn't support streamable http (stateful) arguments
				.filter(ModernMethodSupport.filterLegacyContextMethods())
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

	private McpSyncTool toTool(Object toolObject, Method mcpToolMethod) {
		var toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);
		ModernToolDefinitions.Definition definition = ModernToolDefinitions.define(mcpToolMethod, toolJavaAnnotation,
				this.getJsonMapper());
		return McpSyncTool.of(definition.tool(),
				ModernMethodSupport.requiredCapabilities(mcpToolMethod, toolJavaAnnotation.requiredCapabilities()),
				new SyncModernMcpToolMethodCallback(definition.returnMode(), mcpToolMethod, toolObject,
						this.getJsonMapper()));
	}

}
