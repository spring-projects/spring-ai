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

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.modern.SyncCompletionSpecification;
import org.springframework.ai.mcp.modern.SyncPromptSpecification;
import org.springframework.ai.mcp.modern.SyncResourceSpecification;
import org.springframework.ai.mcp.modern.SyncResourceTemplateSpecification;
import org.springframework.ai.mcp.modern.SyncToolSpecification;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ModernToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Turns beans with {@link McpTool}, {@link McpResource}, {@link McpPrompt} and
 * {@link McpComplete} methods into specifications for the 2026-07-28 ("modern") MCP
 * server.
 * <p>
 * Active whenever the modern server and the annotation scanner are enabled. It is
 * independent of {@code spring.ai.mcp.server.tool-callback-converter}, which only governs
 * {@link ModernToolCallbackConverterAutoConfiguration}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
@AutoConfiguration(after = McpServerAnnotationScannerAutoConfiguration.class)
@ConditionalOnClass({ io.modelcontextprotocol.modern.server.McpServer.class, McpTool.class })
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@Conditional(McpServerModernAutoConfiguration.EnabledModernServerCondition.class)
public class ModernServerSpecificationFactoryAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncToolSpecification> modernToolSpecs(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
		return SyncMcpAnnotationProviders
			.modernToolSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncResourceSpecification> modernResourceSpecs(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
		return SyncMcpAnnotationProviders
			.modernResourceSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncResourceTemplateSpecification> modernResourceTemplateSpecs(
			ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
		return SyncMcpAnnotationProviders.modernResourceTemplateSpecifications(
				beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncPromptSpecification> modernPromptSpecs(ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
		return SyncMcpAnnotationProviders
			.modernPromptSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpPrompt.class));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncCompletionSpecification> modernCompletionSpecs(
			ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
		return SyncMcpAnnotationProviders
			.modernCompleteSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpComplete.class));
	}

}
