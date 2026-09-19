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

package org.springframework.ai.mcp.toolbox.autoconfigure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.tool.Tool;

import org.springframework.ai.mcp.toolbox.McpToolboxToolCallbackProvider;
import org.springframework.ai.mcp.toolbox.aot.McpToolboxRuntimeHints;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.util.StringUtils;

/**
 * Spring Boot Auto-Configuration for Google Cloud MCP Toolbox.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ McpToolboxClient.class, McpToolboxToolCallbackProvider.class })
@ConditionalOnProperty(prefix = McpToolboxProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@EnableConfigurationProperties(McpToolboxProperties.class)
@ImportRuntimeHints(McpToolboxRuntimeHints.class)
public class McpToolboxAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public McpToolboxClient mcpToolboxClient(McpToolboxProperties properties,
			ObjectProvider<McpToolboxClientCustomizer> customizers) {
		McpToolboxClient.Builder builder = McpToolboxClient.builder().baseUrl(properties.getUrl());
		if (StringUtils.hasText(properties.getApiKey())) {
			builder.apiKey(properties.getApiKey());
		}
		if (properties.getProtocolVersion() != null) {
			builder.protocolVersion(properties.getProtocolVersion());
		}

		Map<String, String> mergedHeaders = new LinkedHashMap<>();
		mergedHeaders.put("X-Client-Name", properties.getClientName());
		mergedHeaders.put("X-Client-Version", properties.getClientVersion());
		if (!properties.getHeaders().isEmpty()) {
			mergedHeaders.putAll(properties.getHeaders());
		}
		builder.headers(mergedHeaders);

		customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public McpToolboxToolCallbackProvider mcpToolboxToolCallbackProvider(McpToolboxClient mcpToolboxClient,
			McpToolboxProperties properties, ObjectProvider<Tool> preconfiguredToolsProvider,
			ObjectProvider<ObjectMapper> objectMapperProvider) {
		ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		List<Tool> preconfiguredTools = preconfiguredToolsProvider.orderedStream().toList();
		return McpToolboxToolCallbackProvider.builder()
			.client(mcpToolboxClient)
			.toolsets(properties.getToolsets())
			.tools(properties.getTools())
			.preconfiguredTools(preconfiguredTools)
			.timeout(properties.getTimeout())
			.objectMapper(mapper)
			.build();
	}

}
