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

import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpSampling;

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
@ConditionalOnClass(McpLogging.class)
@ConditionalOnProperty(prefix = McpClientAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpClientAnnotationScannerProperties.class)
public class McpClientAnnotationScannerAutoConfiguration {

	private static final Set<Class<? extends Annotation>> CLIENT_MCP_ANNOTATIONS = Set.of(McpLogging.class,
			McpSampling.class, McpElicitation.class, McpProgress.class);

	@Bean
	@ConditionalOnMissingBean
	public ClientMcpAnnotatedBeans clientAnnotatedBeans() {
		return new ClientMcpAnnotatedBeans();
	}

	@Bean
	@ConditionalOnMissingBean
	public ClientAnnotatedMethodBeanPostProcessor clientAnnotatedMethodBeanPostProcessor(
			ClientMcpAnnotatedBeans clientMcpAnnotatedBeans, McpClientAnnotationScannerProperties properties) {
		return new ClientAnnotatedMethodBeanPostProcessor(clientMcpAnnotatedBeans, CLIENT_MCP_ANNOTATIONS);
	}

	public static class ClientMcpAnnotatedBeans extends AbstractMcpAnnotatedBeans {

	}

	public static class ClientAnnotatedMethodBeanPostProcessor extends AbstractAnnotatedMethodBeanPostProcessor {

		public ClientAnnotatedMethodBeanPostProcessor(ClientMcpAnnotatedBeans clientMcpAnnotatedBeans,
				Set<Class<? extends Annotation>> targetAnnotations) {
			super(clientMcpAnnotatedBeans, targetAnnotations);
		}

	}

}
