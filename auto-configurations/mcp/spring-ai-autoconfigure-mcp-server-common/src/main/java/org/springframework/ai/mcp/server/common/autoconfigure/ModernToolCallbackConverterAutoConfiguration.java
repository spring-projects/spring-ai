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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.modern.McpModernToolUtils;
import org.springframework.ai.mcp.modern.SyncToolSpecification;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.util.MimeType;

/**
 * Exposes every {@link ToolCallback} and {@link ToolCallbackProvider} bean as a tool of
 * the 2026-07-28 ("modern") MCP server.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
@AutoConfiguration
@ConditionalOnClass(io.modelcontextprotocol.modern.server.McpServer.class)
@EnableConfigurationProperties(McpServerProperties.class)
@Conditional({ McpServerModernAutoConfiguration.EnabledModernServerCondition.class,
		StatelessToolCallbackConverterAutoConfiguration.ToolCallbackConverterCondition.class })
public class ModernToolCallbackConverterAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncToolSpecification> modernSyncTools(ObjectProvider<List<ToolCallback>> toolCalls,
			List<ToolCallback> toolCallbackList, ObjectProvider<List<ToolCallbackProvider>> tcbProviderList,
			ObjectProvider<ToolCallbackProvider> tcbProviders, McpServerProperties serverProperties) {
		List<ToolCallback> tools = ToolCallbackUtils.aggregateToolCallbacks(toolCalls, toolCallbackList,
				tcbProviderList, tcbProviders, serverProperties.isExposeMcpClientTools());
		// De-duplicate tools by their name, keeping the first occurrence of each name
		return tools.stream()
			.collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool,
					(existing, replacement) -> existing))
			.values()
			.stream()
			.map(tool -> McpModernToolUtils.toSyncToolSpecification(tool, mimeType(serverProperties, tool)))
			.toList();
	}

	private static @Nullable MimeType mimeType(McpServerProperties serverProperties, ToolCallback tool) {
		String toolName = tool.getToolDefinition().name();
		String mimeType = serverProperties.getToolResponseMimeType().get(toolName);
		return mimeType != null ? MimeType.valueOf(mimeType) : null;
	}

}
