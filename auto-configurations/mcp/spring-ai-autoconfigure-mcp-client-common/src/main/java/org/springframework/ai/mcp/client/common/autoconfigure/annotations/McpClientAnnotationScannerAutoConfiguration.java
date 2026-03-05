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

import java.lang.annotation.Annotation;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;

import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractMcpAnnotatedBeans;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * @author Christian Tzolov
 * @author Josh Long
 * @author Fu Jian
 */
@AutoConfiguration
@ConditionalOnClass(McpLogging.class)
@ConditionalOnProperty(prefix = McpClientAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpClientAnnotationScannerProperties.class)
@ImportRuntimeHints(McpClientAnnotationScannerAutoConfiguration.AnnotationHints.class)
public class McpClientAnnotationScannerAutoConfiguration {

	private static final Set<Class<? extends Annotation>> CLIENT_MCP_ANNOTATIONS = Set.of(McpLogging.class,
			McpSampling.class, McpElicitation.class, McpProgress.class, McpToolListChanged.class,
			McpResourceListChanged.class, McpPromptListChanged.class);

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public ClientMcpSyncHandlersRegistry clientMcpSyncHandlersRegistry() {
		return new ClientMcpSyncHandlersRegistry();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public ClientMcpAsyncHandlersRegistry clientMcpAsyncHandlersRegistry() {
		return new ClientMcpAsyncHandlersRegistry();
	}

	@Bean
	static ClientAnnotatedBeanFactoryInitializationAotProcessor clientAnnotatedBeanFactoryInitializationAotProcessor() {
		return new ClientAnnotatedBeanFactoryInitializationAotProcessor(CLIENT_MCP_ANNOTATIONS);
	}

	public static class ClientMcpAnnotatedBeans extends AbstractMcpAnnotatedBeans {

	}

	public static class ClientAnnotatedBeanFactoryInitializationAotProcessor
			extends AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor {

		public ClientAnnotatedBeanFactoryInitializationAotProcessor(
				Set<Class<? extends Annotation>> targetAnnotations) {
			super(targetAnnotations);
		}

	}

	static class AnnotationHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			CLIENT_MCP_ANNOTATIONS.forEach(an -> hints.reflection().registerType(an, MemberCategory.values()));
		}

	}

}
