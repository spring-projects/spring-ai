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
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;

import org.springframework.ai.mcp.toolbox.McpToolboxToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
public class McpToolboxAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public McpToolboxClient mcpToolboxClient(McpToolboxProperties properties,
			ObjectProvider<McpToolboxClientCustomizer> customizers) {
		McpToolboxClient.Builder builder = McpToolboxClient.builder().baseUrl(properties.getUrl());
		if (StringUtils.hasText(properties.getApiKey())) {
			builder.apiKey(properties.getApiKey());
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
			McpToolboxProperties properties, ObjectProvider<ObjectMapper> objectMapperProvider) {
		ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		return new McpToolboxToolCallbackProvider(mcpToolboxClient, properties.getToolsets(), properties.getTools(),
				properties.getTimeout(), mapper);
	}

}
