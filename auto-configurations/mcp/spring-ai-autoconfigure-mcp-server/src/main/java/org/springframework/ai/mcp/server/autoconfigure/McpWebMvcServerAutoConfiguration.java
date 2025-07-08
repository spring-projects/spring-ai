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
 * {@link AutoConfiguration Auto-configuration} for MCP WebMvc Server Transport.
 * <p>
 * This configuration class sets up the WebMvc-specific transport components for the MCP
 * server, providing Server-Sent Events (SSE) communication through Spring MVC. It is
 * activated when:
 * <ul>
 * <li>The WebMvcSseServerTransport class is on the classpath (from mcp-spring-webmvc
 * dependency)</li>
 * <li>Spring MVC's RouterFunction class is available (from spring-boot-starter-web)</li>
 * <li>The {@code spring.ai.mcp.server.transport} property is set to {@code WEBMVC}</li>
 * </ul>
 * <p>
 * The configuration provides:
 * <ul>
 * <li>A WebMvcSseServerTransport bean for handling SSE communication</li>
 * <li>A RouterFunction bean that sets up the SSE endpoint</li>
 * </ul>
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
 * @since 1.0.0
 * @see McpServerProperties
 * @see WebMvcSseServerTransportProvider
 */
@AutoConfiguration
@ConditionalOnClass({ WebMvcSseServerTransportProvider.class })
@ConditionalOnMissingBean(McpServerTransportProvider.class)
@Conditional(McpServerStdioDisabledCondition.class)
public class McpWebMvcServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		return new WebMvcSseServerTransportProvider(objectMapper, serverProperties.getBaseUrl(),
				serverProperties.getSseMessageEndpoint(), serverProperties.getSseEndpoint());
	}

	@Bean
	public RouterFunction<ServerResponse> mvcMcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
		return transportProvider.getRouterFunction();
	}

}
