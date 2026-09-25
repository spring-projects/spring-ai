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

import java.lang.reflect.Method;
import java.util.List;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.tool.AsyncMcpToolProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;
import org.springframework.ai.mcp.annotation.spring.AnnotationProviderUtil;
import org.springframework.ai.mcp.annotation.spring.AsyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(after = McpServerAnnotationScannerAutoConfiguration.class)
@ConditionalOnClass(McpTool.class)
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@Conditional(McpServerAutoConfiguration.NonStatelessServerCondition.class)
public class McpServerSpecificationFactoryAutoConfiguration {

	private static SyncMcpToolProvider syncToolProvider(List<Object> toolObjects, JsonMapper jsonMapper) {
		var provider = new SyncMcpToolProvider(toolObjects) {
			@Override
			protected Method[] doGetClassMethods(Object bean) {
				return AnnotationProviderUtil.beanMethods(bean);
			}
		};
		provider.setJsonMapper(new JacksonMcpJsonMapper(jsonMapper));
		return provider;
	}

	private static AsyncMcpToolProvider asyncToolProvider(List<Object> toolObjects, JsonMapper jsonMapper) {
		var provider = new AsyncMcpToolProvider(toolObjects) {
			@Override
			protected Method[] doGetClassMethods(Object bean) {
				return AnnotationProviderUtil.beanMethods(bean);
			}
		};
		provider.setJsonMapper(new JacksonMcpJsonMapper(jsonMapper));
		return provider;
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	static class SyncServerSpecificationConfiguration {

		@Bean
		public List<McpServerFeatures.SyncResourceSpecification> resourceSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {

			List<McpServerFeatures.SyncResourceSpecification> syncResourceSpecifications = SyncMcpAnnotationProviders
				.resourceSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
			return syncResourceSpecifications;
		}

		@Bean
		public List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplateSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {

			List<McpServerFeatures.SyncResourceTemplateSpecification> syncResourceTemplateSpecifications = SyncMcpAnnotationProviders
				.resourceTemplateSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
			return syncResourceTemplateSpecifications;
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
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations,
				@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper) {
			List<Object> beansByAnnotation = beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class);
			return syncToolProvider(beansByAnnotation, jsonMapper).getToolSpecifications();
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
		public List<McpServerFeatures.AsyncResourceTemplateSpecification> resourceTemplateSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {

			return AsyncMcpAnnotationProviders
				.resourceTemplateSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
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
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations,
				@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper) {
			return asyncToolProvider(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class), jsonMapper)
				.getToolSpecifications();
		}

	}

}
