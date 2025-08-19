/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.server.stateless.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;

/**
 * @author Christian Tzolov
 */
@EnableConfigurationProperties(McpStatelessServerProperties.class)
@Conditional(ToolCallbackConverterCondition.class)
public class ToolCallbackConverterAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = McpStatelessServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpStatelessServerFeatures.SyncToolSpecification> syncTools(
			ObjectProvider<List<ToolCallback>> toolCalls, List<ToolCallback> toolCallbackList,
			List<ToolCallbackProvider> toolCallbackProvider, McpStatelessServerProperties serverProperties) {

		List<ToolCallback> tools = this.aggregateToolCallbacks(toolCalls, toolCallbackList, toolCallbackProvider);

		return this.toSyncToolSpecifications(tools, serverProperties);
	}

	private List<McpStatelessServerFeatures.SyncToolSpecification> toSyncToolSpecifications(List<ToolCallback> tools,
			McpStatelessServerProperties serverProperties) {

		// De-duplicate tools by their name, keeping the first occurrence of each tool
		// name
		return tools.stream() // Key: tool name
			.collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool,
					(existing, replacement) -> existing))
			.values()
			.stream()
			.map(tool -> {
				String toolName = tool.getToolDefinition().name();
				MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
						? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
				return McpToolUtils.toStatelessSyncToolSpecification(tool, mimeType);
			})
			.toList();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpStatelessServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpStatelessServerFeatures.AsyncToolSpecification> asyncTools(
			ObjectProvider<List<ToolCallback>> toolCalls, List<ToolCallback> toolCallbackList,
			List<ToolCallbackProvider> toolCallbackProvider, McpStatelessServerProperties serverProperties) {

		List<ToolCallback> tools = this.aggregateToolCallbacks(toolCalls, toolCallbackList, toolCallbackProvider);

		return this.toAsyncToolSpecification(tools, serverProperties);
	}

	private List<McpStatelessServerFeatures.AsyncToolSpecification> toAsyncToolSpecification(List<ToolCallback> tools,
			McpStatelessServerProperties serverProperties) {
		// De-duplicate tools by their name, keeping the first occurrence of each tool
		// name
		return tools.stream() // Key: tool name
			.collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool,
					(existing, replacement) -> existing))
			.values()
			.stream()
			.map(tool -> {
				String toolName = tool.getToolDefinition().name();
				MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
						? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
				return McpToolUtils.toStatelessAsyncToolSpecification(tool, mimeType);
			})
			.toList();
	}

	private List<ToolCallback> aggregateToolCallbacks(ObjectProvider<List<ToolCallback>> toolCalls,
			List<ToolCallback> toolCallbacksList, List<ToolCallbackProvider> toolCallbackProvider) {

		List<ToolCallback> tools = new ArrayList<>(toolCalls.stream().flatMap(List::stream).toList());

		if (!CollectionUtils.isEmpty(toolCallbacksList)) {
			tools.addAll(toolCallbacksList);
		}

		List<ToolCallback> providerToolCallbacks = toolCallbackProvider.stream()
			.map(pr -> List.of(pr.getToolCallbacks()))
			.flatMap(List::stream)
			.filter(fc -> fc instanceof ToolCallback)
			.map(fc -> (ToolCallback) fc)
			.toList();

		tools.addAll(providerToolCallbacks);
		return tools;
	}

}
