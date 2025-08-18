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

package org.springframework.ai.mcp.server.stateless.webflux.autoconfigure;

import org.springframework.ai.mcp.server.stateless.autoconfigure.McpStatelessServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;
import org.springframework.web.reactive.function.server.RouterFunction;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * @author Christian Tzolov
 */
@ConditionalOnClass({ McpSchema.class, McpStatelessSyncServer.class })
@EnableConfigurationProperties(McpStatelessServerProperties.class)
@ConditionalOnProperty(prefix = McpStatelessServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpStatelessServerWebFluxAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpStatelessServerWebFluxAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public WebFluxStatelessServerTransport webFluxStatelessServerTransport(
			ObjectProvider<ObjectMapper> objectMapperProvider, McpStatelessServerProperties serverProperties) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		return WebFluxStatelessServerTransport.builder()
			.objectMapper(objectMapper)
			.messageEndpoint(serverProperties.getMcpEndpoint())
			// .disallowDelete(serverProperties.isDisallowDelete())
			.build();
	}

	// Router function for stateless http transport used by Spring WebFlux to start an
	// HTTP server.
	@Bean
	public RouterFunction<?> webFluxStatelessServerRouterFunction(
			WebFluxStatelessServerTransport webFluxStatelessTransport) {
		return webFluxStatelessTransport.getRouterFunction();
	}

}
