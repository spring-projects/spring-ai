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

package org.springframework.ai.mcp.server.common.autoconfigure.annotations;

import java.util.List;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.server.feature.McpSyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.modern.SyncCompletionSpecification;
import org.springframework.ai.mcp.modern.SyncResourceEntry;
import org.springframework.ai.mcp.modern.SyncResourceTemplateEntry;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ModernToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Turns beans with {@link McpTool}, {@link McpResource}, {@link McpPrompt} and
 * {@link McpComplete} methods into the tools, resources, prompts and completions of the
 * 2026-07-28 ("modern") MCP server.
 * <p>
 * Active whenever the modern server and the annotation scanner are enabled. It is
 * independent of {@code spring.ai.mcp.server.tool-callback-converter}, which only governs
 * {@link ModernToolCallbackConverterAutoConfiguration}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@AutoConfiguration(after = { McpServerAnnotationScannerAutoConfiguration.class,
		McpServerJsonMapperAutoConfiguration.class })
@ConditionalOnClass({ io.modelcontextprotocol.modern.server.McpServer.class, McpTool.class })
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@Conditional(McpServerModernAutoConfiguration.EnabledModernServerCondition.class)
public class ModernServerSpecificationFactoryAutoConfiguration {

	// The server's mapper, so that tool schemas and MRTR input responses are handled
	// the way the server serialises its messages. Absent in a context without the
	// JSON mapper auto-configuration, in which case the SDK default is used.
	private static @Nullable McpJsonMapper serverMapper(ObjectProvider<JsonMapper> jsonMapper) {
		JsonMapper mapper = jsonMapper.getIfAvailable();
		return mapper != null ? new JacksonMcpJsonMapper(mapper) : null;
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpSyncTool> modernTools(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations,
			@Qualifier("mcpServerJsonMapper") ObjectProvider<JsonMapper> jsonMapper) {
		return SyncMcpAnnotationProviders.modernTools(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class),
				serverMapper(jsonMapper));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncResourceEntry> modernResources(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations,
			@Qualifier("mcpServerJsonMapper") ObjectProvider<JsonMapper> jsonMapper) {
		return SyncMcpAnnotationProviders.modernResources(
				beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class), serverMapper(jsonMapper));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncResourceTemplateEntry> modernResourceTemplates(
			ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations,
			@Qualifier("mcpServerJsonMapper") ObjectProvider<JsonMapper> jsonMapper) {
		return SyncMcpAnnotationProviders.modernResourceTemplates(
				beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class), serverMapper(jsonMapper));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpSyncPrompt> modernPrompts(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations,
			@Qualifier("mcpServerJsonMapper") ObjectProvider<JsonMapper> jsonMapper) {
		return SyncMcpAnnotationProviders.modernPrompts(
				beansWithMcpMethodAnnotations.getBeansByAnnotation(McpPrompt.class), serverMapper(jsonMapper));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncCompletionSpecification> modernCompletions(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
		return SyncMcpAnnotationProviders
			.modernCompleteSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpComplete.class));
	}

}
