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

package org.springframework.ai.mcp.server.common.autoconfigure.annotations;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;

import org.springframework.ai.mcp.annotation.spring.AsyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(after = ServerAnnotationScannerAutoConfiguration.class)
@ConditionalOnProperty(prefix = ServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@Conditional(McpServerAutoConfiguration.NonStatlessServerCondition.class)
public class ServerSpecificationFactoryAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	static class SyncServerSpecificationConfiguration {

		@Bean
		public List<McpServerFeatures.SyncResourceSpecification> resourceSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.resourceSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
		}

		@Bean
		public List<McpServerFeatures.SyncPromptSpecification> promptSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.promptSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpPrompt.class));
		}

		@Bean
		public List<McpServerFeatures.SyncCompletionSpecification> completionSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.completeSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpComplete.class));
		}

		@Bean
		public List<McpServerFeatures.SyncToolSpecification> toolSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.toolSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	static class AsyncServerSpecificationConfiguration {

		@Bean
		public List<McpServerFeatures.AsyncResourceSpecification> resourceSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.resourceSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
		}

		@Bean
		public List<McpServerFeatures.AsyncPromptSpecification> promptSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.promptSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpPrompt.class));
		}

		@Bean
		public List<McpServerFeatures.AsyncCompletionSpecification> completionSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.completeSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpComplete.class));
		}

		@Bean
		public List<McpServerFeatures.AsyncToolSpecification> toolSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.toolSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class));
		}

	}

}
