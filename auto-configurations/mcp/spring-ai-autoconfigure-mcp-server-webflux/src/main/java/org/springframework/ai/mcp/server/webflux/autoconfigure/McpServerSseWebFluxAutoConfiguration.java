/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * {@link AutoConfiguration Auto-configuration} for MCP WebFlux Server Transport.
 * <p>
 * This configuration class sets up the WebFlux-specific transport components for the MCP
 * server, providing reactive Server-Sent Events (SSE) communication through Spring
 * WebFlux. It is activated when:
 * <ul>
 * <li>The WebFluxSseServerTransportProvider class is on the classpath (from
 * mcp-spring-webflux dependency)</li>
 * <li>Spring WebFlux's RouterFunction class is available (from
 * spring-boot-starter-webflux)</li>
 * <li>The {@code spring.ai.mcp.server.transport} property is set to {@code WEBFLUX}</li>
 * </ul>
 * <p>
 * The configuration provides:
 * <ul>
 * <li>A WebFluxSseServerTransportProvider bean for handling reactive SSE
 * communication</li>
 * <li>A RouterFunction bean that sets up the reactive SSE endpoint</li>
 * </ul>
 * <p>
 * Required dependencies:
 *
 * <pre>{@code
 * <dependency>
 *     <groupId>org.springframework.ai</groupId>
 *     <artifactId>mcp-spring-webflux</artifactId>
 * </dependency>
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-webflux</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Yanming Zhou
 * @since 1.0.0
 * @see McpServerSseProperties
 * @see WebFluxSseServerTransportProvider
 */
@AutoConfiguration(before = McpServerAutoConfiguration.class)
@EnableConfigurationProperties(McpServerSseProperties.class)
@ConditionalOnClass(WebFluxSseServerTransportProvider.class)
@ConditionalOnMissingBean(McpServerTransportProvider.class)
@Conditional({ McpServerStdioDisabledCondition.class, McpServerAutoConfiguration.EnabledSseServerCondition.class })
public class McpServerSseWebFluxAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebFluxSseServerTransportProvider webFluxTransport(McpServerSseProperties serverProperties) {

		var builder = WebFluxSseServerTransportProvider.builder()
			.jsonMapper(McpJsonMapper.getDefault())
			.basePath(serverProperties.getBaseUrl())
			.messageEndpoint(serverProperties.getSseMessageEndpoint())
			.sseEndpoint(serverProperties.getSseEndpoint());
		if (serverProperties.getKeepAliveInterval() != null) {
			builder.keepAliveInterval(serverProperties.getKeepAliveInterval());
		}
		return builder.build();
	}

	// Router function for SSE transport used by Spring WebFlux to start an HTTP
	// server.
	@Bean
	@ConditionalOnMissingBean(name = "webfluxSseServerRouterFunction")
	public RouterFunction<?> webfluxSseServerRouterFunction(WebFluxSseServerTransportProvider webFluxProvider) {
		return webFluxProvider.getRouterFunction();
	}

}
