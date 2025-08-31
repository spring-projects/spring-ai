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

import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;

import org.springframework.ai.mcp.annotation.spring.scan.AbstractAnnotatedMethodBeanPostProcessor;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractMcpAnnotatedBeans;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration
@ConditionalOnClass(McpTool.class)
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerAnnotationScannerProperties.class)
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
	public ServerAnnotatedMethodBeanPostProcessor serverAnnotatedMethodBeanPostProcessor(
			ServerMcpAnnotatedBeans serverMcpAnnotatedBeans, McpServerAnnotationScannerProperties properties) {
		return new ServerAnnotatedMethodBeanPostProcessor(serverMcpAnnotatedBeans, SERVER_MCP_ANNOTATIONS);
	}

	public static class ServerMcpAnnotatedBeans extends AbstractMcpAnnotatedBeans {

	}

	public static class ServerAnnotatedMethodBeanPostProcessor extends AbstractAnnotatedMethodBeanPostProcessor {

		public ServerAnnotatedMethodBeanPostProcessor(ServerMcpAnnotatedBeans serverMcpAnnotatedBeans,
				Set<Class<? extends Annotation>> targetAnnotations) {
			super(serverMcpAnnotatedBeans, targetAnnotations);
		}

	}

}
