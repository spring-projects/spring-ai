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
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * {@link AutoConfiguration Auto-configuration} for MCP WebMvc Streamable Server
 * Transport.
 * <p>
 * This configuration class sets up the WebMvc-specific Streamable transport components
 * for the MCP server, providing HTTP streaming communication through Spring MVC. It is
 * activated when:
 * <ul>
 * <li>The WebMvcStreamableServerTransportProvider class is on the classpath (from
 * mcp-spring-webmvc dependency)</li>
 * <li>Spring MVC's RouterFunction class is available (from spring-boot-starter-web)</li>
 * <li>The MCP server is enabled and STDIO transport is disabled</li>
 * <li>The transport type is set to STREAMABLE</li>
 * </ul>
 * <p>
 * The configuration provides:
 * <ul>
 * <li>A WebMvcStreamableServerTransportProvider bean for handling HTTP streaming
 * communication</li>
 * <li>A RouterFunction bean that sets up the streaming endpoint</li>
 * </ul>
 * <p>
 * Required dependencies: <pre>{@code
 * <dependency>
 *     <groupId>io.modelcontextprotocol.sdk</groupId>
 *     <artifactId>mcp-spring-webmvc</artifactId>
 * </dependency>
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-web</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author yinh
 * @since 1.1.0
 * @see McpServerProperties
 * @see WebMvcStreamableServerTransportProvider
 */
@AutoConfiguration
@ConditionalOnClass({ WebMvcStreamableServerTransportProvider.class, RouterFunction.class })
@ConditionalOnMissingBean(McpServerTransportProvider.class)
@Conditional(McpServerStreamableTransportCondition.class)
public class McpWebMvcStreamableServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebMvcStreamableServerTransportProvider webMvcStreamableTransport(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		// 使用builder模式创建WebMvc Streamable传输提供者
		return WebMvcStreamableServerTransportProvider.builder()
			.mcpEndpoint(serverProperties.getSseMessageEndpoint())
			.objectMapper(objectMapper)
			.build();
	}

	@Bean
	public RouterFunction<ServerResponse> mvcMcpStreamableRouterFunction(
			WebMvcStreamableServerTransportProvider transportProvider) {
		return transportProvider.getRouterFunction();
	}

}
