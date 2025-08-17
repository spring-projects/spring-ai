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
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * {@link AutoConfiguration Auto-configuration} for MCP HttpServlet Streamable Server
 * Transport.
 * <p>
 * This configuration class sets up the HttpServlet-specific Streamable transport
 * components for the MCP server, providing HTTP streaming communication through standard
 * Java Servlet API. It is activated when:
 * <ul>
 * <li>The HttpServletStreamableServerTransportProvider class is on the classpath (from
 * mcp-server-http-servlet dependency)</li>
 * <li>The MCP server is enabled and STDIO transport is disabled</li>
 * <li>The transport type is set to STREAMABLE</li>
 * <li>No other MCP server transport provider is available</li>
 * </ul>
 * <p>
 * The configuration provides:
 * <ul>
 * <li>A HttpServletStreamableServerTransportProvider bean for handling HTTP streaming
 * communication</li>
 * </ul>
 * <p>
 * This configuration has the lowest priority and will only be activated if no WebFlux or
 * WebMVC streamable transports are available.
 * <p>
 * Required dependencies: <pre>{@code
 * <dependency>
 *     <groupId>io.modelcontextprotocol.sdk</groupId>
 *     <artifactId>mcp-server-http-servlet</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author yinh
 * @since 1.0.1
 * @see McpServerProperties
 * @see HttpServletStreamableServerTransportProvider
 */
@AutoConfiguration(
		after = { McpWebFluxStreamableServerAutoConfiguration.class, McpWebMvcStreamableServerAutoConfiguration.class })
@ConditionalOnClass({ HttpServletStreamableServerTransportProvider.class })
@ConditionalOnMissingBean(McpServerTransportProvider.class)
@Conditional(McpServerStreamableTransportCondition.class)
public class McpHttpServletStreamableServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public HttpServletStreamableServerTransportProvider httpServletStreamableTransport(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		// Create an HttpServlet Streamable transport provider using the builder pattern
		return HttpServletStreamableServerTransportProvider.builder()
			.mcpEndpoint(serverProperties.getSseMessageEndpoint())
			.objectMapper(objectMapper)
			.build();
	}

}
