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

package org.springframework.ai.mcp.server.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * {@link AutoConfiguration Auto-configuration} for MCP WebFlux Server Transport.
 * <p>
 * This configuration class sets up the WebFlux-specific transport components for the MCP
 * server, providing both reactive Server-Sent Events (SSE) and stateless HTTP
 * communication through Spring WebFlux. It is activated when:
 * <ul>
 * <li>The WebFluxSseServerTransportProvider and WebFluxStatelessServerTransport classes
 * are on the classpath (from mcp-spring-webflux dependency)</li>
 * <li>Spring WebFlux's RouterFunction class is available (from
 * spring-boot-starter-webflux)</li>
 * <li>STDIO transport is disabled</li>
 * </ul>
 * <p>
 * The configuration provides transport beans based on server type:
 * <ul>
 * <li>For ASYNC servers: WebFluxSseServerTransportProvider for reactive SSE communication</li>
 * <li>For ASYNC_STATELESS servers: WebFluxStatelessServerTransport for stateless reactive HTTP communication</li>
 * <li>RouterFunction beans for setting up the appropriate endpoints</li>
 * </ul>
 * <p>
 * Stateless transport (ASYNC_STATELESS) handles reactive HTTP requests without maintaining
 * session state, making it suitable for scenarios where session management is handled
 * externally or not required. It uses the {@code spring.ai.mcp.server.stateless-message-endpoint}
 * property to configure the endpoint path.
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
 * @author Yanming Zhou
 * @since 1.0.0
 * @see McpServerProperties
 * @see WebFluxSseServerTransportProvider
 * @see WebFluxStatelessServerTransport
 */
@AutoConfiguration
@ConditionalOnClass({ WebFluxSseServerTransportProvider.class, WebFluxStatelessServerTransport.class })
@Conditional(McpServerStdioDisabledCondition.class)
public class McpWebFluxServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC",
			matchIfMissing = true)
	public WebFluxSseServerTransportProvider webFluxTransport(ObjectProvider<ObjectMapper> objectMapperProvider,
			McpServerProperties serverProperties) {
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		return new WebFluxSseServerTransportProvider(objectMapper, serverProperties.getBaseUrl(),
				serverProperties.getSseMessageEndpoint(), serverProperties.getSseEndpoint());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC_STATELESS")
	public WebFluxStatelessServerTransport webFluxStatelessTransport(ObjectProvider<ObjectMapper> objectMapperProvider,
											 McpServerProperties serverProperties) {
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		return WebFluxStatelessServerTransport.builder()
				.objectMapper(objectMapper)
				.messageEndpoint(serverProperties.getStatelessMessageEndpoint())
				.build();
	}

	// Router function for SSE transport used by Spring WebFlux to start an HTTP server.
	@Bean
	@ConditionalOnBean(WebFluxSseServerTransportProvider.class)
	public RouterFunction<?> webfluxMcpRouterFunction(WebFluxSseServerTransportProvider webFluxProvider) {
		return webFluxProvider.getRouterFunction();
	}

	// Router function for Stateless SSE transport used by Spring WebFlux to start an HTTP server.
	@Bean
	@ConditionalOnBean(WebFluxStatelessServerTransport.class)
	public RouterFunction<?> webfluxStatelessMcpRouterFunction(WebFluxStatelessServerTransport webFluxProvider) {
		return webFluxProvider.getRouterFunction();
	}

}
