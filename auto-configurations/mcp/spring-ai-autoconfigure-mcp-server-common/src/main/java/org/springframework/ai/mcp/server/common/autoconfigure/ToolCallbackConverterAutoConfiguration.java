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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.modelcontextprotocol.server.McpServerFeatures;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * @author Christian Tzolov, He-Pin
 */
@AutoConfiguration
@EnableConfigurationProperties(McpServerProperties.class)
@Conditional({ ToolCallbackConverterAutoConfiguration.ToolCallbackConverterCondition.class,
		McpServerAutoConfiguration.NonStatelessServerCondition.class })
public class ToolCallbackConverterAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpServerFeatures.SyncToolSpecification> syncTools(ObjectProvider<List<ToolCallback>> toolCalls,
			List<ToolCallback> toolCallbackList, ObjectProvider<List<ToolCallbackProvider>> tcbProviderList,
			ObjectProvider<ToolCallbackProvider> tcbProviders, McpServerProperties serverProperties) {

		List<ToolCallback> tools = this.aggregateToolCallbacks(toolCalls, toolCallbackList, tcbProviderList,
				tcbProviders);

		return this.toSyncToolSpecifications(tools, serverProperties);
	}

	private List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecifications(List<ToolCallback> tools,
			McpServerProperties serverProperties) {

		// De-duplicate tools by their name, keeping the first occurrence of each tool
		// name
		return tools.stream() // Key: tool name
			.collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool,
					(existing, replacement) -> existing)) // On duplicate key, keep the
															// existing tool
			.values()
			.stream()
			.map(tool -> {
				String toolName = tool.getToolDefinition().name();
				MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
						? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
				return createSyncToolSpecification(tool, mimeType);
			})
			.toList();
	}

	/**
	 * An extension point to customize the creation of SyncToolSpecification from
	 * ToolCallback.
	 */
	protected McpServerFeatures.SyncToolSpecification createSyncToolSpecification(ToolCallback toolCallback,
			MimeType mimeType) {
		return McpToolUtils.toSyncToolSpecification(toolCallback, mimeType);
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpServerFeatures.AsyncToolSpecification> asyncTools(ObjectProvider<List<ToolCallback>> toolCalls,
			List<ToolCallback> toolCallbacksList, ObjectProvider<List<ToolCallbackProvider>> tcbProviderList,
			ObjectProvider<ToolCallbackProvider> tcbProviders, McpServerProperties serverProperties) {

		List<ToolCallback> tools = this.aggregateToolCallbacks(toolCalls, toolCallbacksList, tcbProviderList,
				tcbProviders);

		return this.toAsyncToolSpecification(tools, serverProperties);
	}

	private List<McpServerFeatures.AsyncToolSpecification> toAsyncToolSpecification(List<ToolCallback> tools,
			McpServerProperties serverProperties) {
		// De-duplicate tools by their name, keeping the first occurrence of each tool
		// name
		return tools.stream() // Key: tool name
			.collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool, // Value:
																								// the
																								// tool
																								// itself
					(existing, replacement) -> existing)) // On duplicate key, keep the
															// existing tool
			.values()
			.stream()
			.map(tool -> {
				String toolName = tool.getToolDefinition().name();
				MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
						? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
				return createAsyncToolSpecification(tool, mimeType);
			})
			.toList();
	}

	/**
	 * An extension point to customize the creation of AsyncToolSpecification from
	 * ToolCallback.
	 */
	protected McpServerFeatures.AsyncToolSpecification createAsyncToolSpecification(ToolCallback toolCallback,
			MimeType mimeType) {
		return McpToolUtils.toAsyncToolSpecification(toolCallback, mimeType);
	}

	private List<ToolCallback> aggregateToolCallbacks(ObjectProvider<List<ToolCallback>> toolCalls,
			List<ToolCallback> toolCallbackList, ObjectProvider<List<ToolCallbackProvider>> tcbProviderList,
			ObjectProvider<ToolCallbackProvider> tcbProviders) {

		// Merge ToolCallbackProviders from both ObjectProviders.
		List<ToolCallbackProvider> totalToolCallbackProviders = new ArrayList<>(
				tcbProviderList.stream().flatMap(List::stream).toList());
		totalToolCallbackProviders.addAll(tcbProviders.stream().toList());

		// De-duplicate ToolCallbackProviders
		totalToolCallbackProviders = totalToolCallbackProviders.stream().distinct().toList();

		List<ToolCallback> tools = new ArrayList<>(toolCalls.stream().flatMap(List::stream).toList());

		if (!CollectionUtils.isEmpty(toolCallbackList)) {
			tools.addAll(toolCallbackList);
		}

		List<ToolCallback> providerToolCallbacks = totalToolCallbackProviders.stream()
			.map(pr -> List.of(pr.getToolCallbacks()))
			.flatMap(List::stream)
			.filter(fc -> fc instanceof ToolCallback)
			.map(fc -> (ToolCallback) fc)
			.toList();

		tools.addAll(providerToolCallbacks);
		return tools;
	}

	public static class ToolCallbackConverterCondition extends AllNestedConditions {

		public ToolCallbackConverterCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		static class McpServerEnabledCondition {

		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "tool-callback-converter",
				havingValue = "true", matchIfMissing = true)
		static class ToolCallbackConvertCondition {

		}

	}

}
