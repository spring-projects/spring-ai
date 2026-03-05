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

import java.lang.annotation.Annotation;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;

import org.springframework.ai.mcp.annotation.spring.scan.AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractAnnotatedMethodBeanPostProcessor;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractMcpAnnotatedBeans;
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
 */
@AutoConfiguration
@ConditionalOnClass(McpTool.class)
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerAnnotationScannerProperties.class)
@ImportRuntimeHints(McpServerAnnotationScannerAutoConfiguration.AnnotationHints.class)
public class McpServerAnnotationScannerAutoConfiguration {

	private static final Set<Class<? extends Annotation>> SERVER_MCP_ANNOTATIONS = Set.of(McpTool.class,
			McpResource.class, McpPrompt.class, McpComplete.class);

	@Bean
	@ConditionalOnMissingBean
	public ServerMcpAnnotatedBeans serverAnnotatedBeanRegistry() {
		return new ServerMcpAnnotatedBeans();
	}

	@Bean
	@ConditionalOnMissingBean
	public static ServerAnnotatedMethodBeanPostProcessor serverAnnotatedMethodBeanPostProcessor(
			ServerMcpAnnotatedBeans serverMcpAnnotatedBeans, McpServerAnnotationScannerProperties properties) {
		return new ServerAnnotatedMethodBeanPostProcessor(serverMcpAnnotatedBeans, SERVER_MCP_ANNOTATIONS);
	}

	@Bean
	public static ServerAnnotatedBeanFactoryInitializationAotProcessor serverAnnotatedBeanFactoryInitializationAotProcessor() {
		return new ServerAnnotatedBeanFactoryInitializationAotProcessor(SERVER_MCP_ANNOTATIONS);
	}

	public static class ServerMcpAnnotatedBeans extends AbstractMcpAnnotatedBeans {

	}

	public static class ServerAnnotatedBeanFactoryInitializationAotProcessor
			extends AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor {

		public ServerAnnotatedBeanFactoryInitializationAotProcessor(
				Set<Class<? extends Annotation>> targetAnnotations) {
			super(targetAnnotations);
		}

	}

	public static class ServerAnnotatedMethodBeanPostProcessor extends AbstractAnnotatedMethodBeanPostProcessor {

		public ServerAnnotatedMethodBeanPostProcessor(ServerMcpAnnotatedBeans serverMcpAnnotatedBeans,
				Set<Class<? extends Annotation>> targetAnnotations) {
			super(serverMcpAnnotatedBeans, targetAnnotations);
		}

	}

	static class AnnotationHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			SERVER_MCP_ANNOTATIONS.forEach(an -> hints.reflection().registerType(an, MemberCategory.values()));
		}

	}

}
