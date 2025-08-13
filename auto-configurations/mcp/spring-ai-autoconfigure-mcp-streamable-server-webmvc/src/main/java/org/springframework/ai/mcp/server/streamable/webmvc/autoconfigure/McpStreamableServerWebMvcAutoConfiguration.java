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

package org.springframework.ai.mcp.server.streamable.webmvc.autoconfigure;

import org.springframework.ai.mcp.server.streamable.autoconfigure.McpStreamableServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;
import org.springframework.web.servlet.function.RouterFunction;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * @author Christian Tzolov
 */
@ConditionalOnClass({ McpSchema.class, McpSyncServer.class })
@EnableConfigurationProperties(McpStreamableServerProperties.class)
@ConditionalOnProperty(prefix = McpStreamableServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpStreamableServerWebMvcAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpStreamableServerWebMvcAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpStreamableServerProperties serverProperties) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		return WebMvcStreamableServerTransportProvider.builder()
			.objectMapper(objectMapper)
			.mcpEndpoint(serverProperties.getMcpEndpoint())
			.keepAliveInterval(serverProperties.getKeepAliveInterval())
			.disallowDelete(serverProperties.isDisallowDelete())
			.build();
	}

	// Router function for streamable http transport used by Spring WebFlux to start an
	// HTTP server.
	@Bean
	public RouterFunction<?> webMvcStreamableServerRouterFunction(
			WebMvcStreamableServerTransportProvider webMvcProvider) {
		return webMvcProvider.getRouterFunction();
	}

}
