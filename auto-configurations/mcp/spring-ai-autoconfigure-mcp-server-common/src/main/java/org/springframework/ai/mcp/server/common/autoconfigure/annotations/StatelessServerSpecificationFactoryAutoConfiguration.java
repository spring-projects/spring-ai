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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;

import org.springframework.ai.mcp.annotation.spring.AsyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.ClassUtils;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(after = McpServerAnnotationScannerAutoConfiguration.class)
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@Conditional({ McpServerStdioDisabledCondition.class,
		McpServerStatelessAutoConfiguration.EnabledStatelessServerCondition.class,
		StatelessToolCallbackConverterAutoConfiguration.ToolCallbackConverterCondition.class })
public class StatelessServerSpecificationFactoryAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(
			StatelessServerSpecificationFactoryAutoConfiguration.class);

	private static final Set<Class<?>> STATELESS_UNSUPPORTED_TOOL_PARAMETER_TYPES = Set.of(McpSyncServerExchange.class,
			McpAsyncServerExchange.class, McpSyncRequestContext.class, McpAsyncRequestContext.class);

	private static void logSkippedStatelessToolMethods(List<Object> toolBeans) {
		for (Object bean : toolBeans) {
			Class<?> beanClass = ClassUtils.getUserClass(bean);
			for (Method method : beanClass.getMethods()) {
				if (!AnnotatedElementUtils.hasAnnotation(method, McpTool.class)) {
					continue;
				}
				List<String> unsupportedParameterTypes = Arrays.stream(method.getParameterTypes())
					.filter(STATELESS_UNSUPPORTED_TOOL_PARAMETER_TYPES::contains)
					.map(Class::getSimpleName)
					.distinct()
					.collect(Collectors.toList());
				if (!unsupportedParameterTypes.isEmpty()) {
					logger.warn(() -> String.format(
							"MCP stateless mode skipped @McpTool method %s#%s because it uses unsupported parameter type(s): %s. Use McpTransportContext or switch to stateful protocol.",
							beanClass.getName(), method.getName(), String.join(", ", unsupportedParameterTypes)));
				}
			}
		}
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	static class SyncStatelessServerSpecificationConfiguration {

		@Bean
		public List<McpStatelessServerFeatures.SyncResourceSpecification> resourceSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.statelessResourceSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncResourceTemplateSpecification> resourceTemplateSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders.statelessResourceTemplateSpecifications(
					beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncPromptSpecification> promptSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.statelessPromptSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpPrompt.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncCompletionSpecification> completionSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.statelessCompleteSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpComplete.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			List<Object> beansByAnnotation = beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class);
			logSkippedStatelessToolMethods(beansByAnnotation);
			List<McpStatelessServerFeatures.SyncToolSpecification> syncToolSpecifications = SyncMcpAnnotationProviders
				.statelessToolSpecifications(beansByAnnotation);
			return syncToolSpecifications;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	static class AsyncStatelessServerSpecificationConfiguration {

		@Bean
		public List<McpStatelessServerFeatures.AsyncResourceSpecification> resourceSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.statelessResourceSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.AsyncResourceTemplateSpecification> resourceTemplateSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders.statelessResourceTemplateSpecifications(
					beansWithMcpMethodAnnotations.getBeansByAnnotation(McpResource.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.AsyncPromptSpecification> promptSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.statelessPromptSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpPrompt.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.AsyncCompletionSpecification> completionSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return AsyncMcpAnnotationProviders
				.statelessCompleteSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpComplete.class));
		}

		@Bean
		public List<McpStatelessServerFeatures.AsyncToolSpecification> toolSpecs(
				ServerMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			List<Object> beansByAnnotation = beansWithMcpMethodAnnotations.getBeansByAnnotation(McpTool.class);
			logSkippedStatelessToolMethods(beansByAnnotation);
			return AsyncMcpAnnotationProviders.statelessToolSpecifications(beansByAnnotation);
		}

	}

}
