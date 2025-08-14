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
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * {@link AutoConfiguration Auto-configuration} for MCP WebMvc Server Transport.
 * <p>
 * This configuration class sets up the WebMvc-specific transport components for the MCP
 * server, providing both Server-Sent Events (SSE) and stateless HTTP communication
 * through Spring MVC. It is activated when:
 * <ul>
 * <li>The WebMvcSseServerTransportProvider and WebMvcStatelessServerTransport classes
 * are on the classpath (from mcp-spring-webmvc dependency)</li>
 * <li>Spring MVC's RouterFunction class is available (from spring-boot-starter-web)</li>
 * <li>STDIO transport is disabled</li>
 * </ul>
 * <p>
 * The configuration provides transport beans based on server type:
 * <ul>
 * <li>For SYNC servers: WebMvcSseServerTransportProvider for SSE communication</li>
 * <li>For SYNC_STATELESS servers: WebMvcStatelessServerTransport for stateless HTTP communication</li>
 * <li>RouterFunction beans for setting up the appropriate endpoints</li>
 * </ul>
 * <p>
 * Stateless transport (SYNC_STATELESS) handles HTTP requests without maintaining session
 * state, making it suitable for scenarios where session management is handled externally
 * or not required. It uses the {@code spring.ai.mcp.server.stateless-message-endpoint}
 * property to configure the endpoint path.
 * <p>
 * Required dependencies: <pre>{@code
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-web</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Yanming Zhou
 * @see McpServerProperties
 * @see WebMvcSseServerTransportProvider
 * @see WebMvcStatelessServerTransport
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({WebMvcSseServerTransportProvider.class, WebMvcStatelessServerTransport.class})
@Conditional(McpServerStdioDisabledCondition.class)
public class McpWebMvcServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		return new WebMvcSseServerTransportProvider(objectMapper, serverProperties.getBaseUrl(),
				serverProperties.getSseMessageEndpoint(), serverProperties.getSseEndpoint());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC_STATELESS")
	public WebMvcStatelessServerTransport webMvcStatelessServerTransportProvider(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		return WebMvcStatelessServerTransport.builder()
				.objectMapper(objectMapper)
				.messageEndpoint(serverProperties.getStatelessMessageEndpoint())
				.build();
	}

	@Bean
	@ConditionalOnBean(WebMvcSseServerTransportProvider.class)
	public RouterFunction<ServerResponse> mvcMcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
		return transportProvider.getRouterFunction();
	}

	@Bean
	@ConditionalOnBean(WebMvcStatelessServerTransport.class)
	public RouterFunction<ServerResponse> mvcStatelessMcpRouterFunction(WebMvcStatelessServerTransport transportProvider) {
		return transportProvider.getRouterFunction();
	}

}
