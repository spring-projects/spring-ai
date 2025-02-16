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

package org.springframework.ai.autoconfigure.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransport;
import io.modelcontextprotocol.spec.ServerMcpTransport;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * {@link AutoConfiguration Auto-configuration} for MCP WebFlux Server Transport.
 * <p>
 * This configuration class sets up the WebFlux-specific transport components for the MCP
 * server, providing reactive Server-Sent Events (SSE) communication through Spring
 * WebFlux. It is activated when:
 * <ul>
 * <li>The WebFluxSseServerTransport class is on the classpath (from mcp-spring-webflux
 * dependency)</li>
 * <li>Spring WebFlux's RouterFunction class is available (from
 * spring-boot-starter-webflux)</li>
 * <li>The {@code spring.ai.mcp.server.transport} property is set to {@code WEBFLUX}</li>
 * </ul>
 * <p>
 * The configuration provides:
 * <ul>
 * <li>A WebFluxSseServerTransport bean for handling reactive SSE communication</li>
 * <li>A RouterFunction bean that sets up the reactive SSE endpoint</li>
 * </ul>
 * <p>
 * Required dependencies: <pre>{@code
 * <dependency>
 *     <groupId>io.modelcontextprotocol.sdk</groupId>
 *     <artifactId>mcp-spring-webflux</artifactId>
 * </dependency>
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-webflux</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see McpServerProperties
 * @see WebFluxSseServerTransport
 */
@AutoConfiguration
@ConditionalOnClass({ WebFluxSseServerTransport.class })
@ConditionalOnMissingBean(ServerMcpTransport.class)
@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "stdio", havingValue = "false",
		matchIfMissing = true)
public class MpcWebFluxServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebFluxSseServerTransport webFluxTransport(McpServerProperties serverProperties) {
		return new WebFluxSseServerTransport(new ObjectMapper(), serverProperties.getSseMessageEndpoint());
	}

	// Router function for SSE transport used by Spring WebFlux to start an HTTP server.
	@Bean
	public RouterFunction<?> webfluxMcpRouterFunction(WebFluxSseServerTransport webFluxTransport) {
		return webFluxTransport.getRouterFunction();
	}

}
