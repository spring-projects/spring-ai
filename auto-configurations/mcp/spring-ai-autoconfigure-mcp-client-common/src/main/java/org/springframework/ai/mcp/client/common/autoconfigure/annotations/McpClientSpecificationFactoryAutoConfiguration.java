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

package org.springframework.ai.mcp.client.common.autoconfigure.annotations;

import java.util.List;

import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.method.elicitation.AsyncElicitationSpecification;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.AsyncLoggingSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.AsyncProgressSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.AsyncSamplingSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;

import org.springframework.ai.mcp.annotation.spring.AsyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration.ClientMcpAnnotatedBeans;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(after = McpClientAnnotationScannerAutoConfiguration.class)
@ConditionalOnClass(McpLogging.class)
@ConditionalOnProperty(prefix = McpClientAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
public class McpClientSpecificationFactoryAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	static class SyncClientSpecificationConfiguration {

		@Bean
		List<SyncLoggingSpecification> loggingSpecs(ClientMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.loggingSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpLogging.class));
		}

		@Bean
		List<SyncSamplingSpecification> samplingSpecs(ClientMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.samplingSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpSampling.class));
		}

		@Bean
		List<SyncElicitationSpecification> elicitationSpecs(ClientMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.elicitationSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpElicitation.class));
		}

		@Bean
		List<SyncProgressSpecification> progressSpecs(ClientMcpAnnotatedBeans beansWithMcpMethodAnnotations) {
			return SyncMcpAnnotationProviders
				.progressSpecifications(beansWithMcpMethodAnnotations.getBeansByAnnotation(McpProgress.class));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	static class AsyncClientSpecificationConfiguration {

		@Bean
		List<AsyncLoggingSpecification> loggingSpecs(ClientMcpAnnotatedBeans beanRegistry) {
			return AsyncMcpAnnotationProviders.loggingSpecifications(beanRegistry.getAllAnnotatedBeans());
		}

		@Bean
		List<AsyncSamplingSpecification> samplingSpecs(ClientMcpAnnotatedBeans beanRegistry) {
			return AsyncMcpAnnotationProviders.samplingSpecifications(beanRegistry.getAllAnnotatedBeans());
		}

		@Bean
		List<AsyncElicitationSpecification> elicitationSpecs(ClientMcpAnnotatedBeans beanRegistry) {
			return AsyncMcpAnnotationProviders.elicitationSpecifications(beanRegistry.getAllAnnotatedBeans());
		}

		@Bean
		List<AsyncProgressSpecification> progressSpecs(ClientMcpAnnotatedBeans beanRegistry) {
			return AsyncMcpAnnotationProviders.progressSpecifications(beanRegistry.getAllAnnotatedBeans());
		}

	}

}
