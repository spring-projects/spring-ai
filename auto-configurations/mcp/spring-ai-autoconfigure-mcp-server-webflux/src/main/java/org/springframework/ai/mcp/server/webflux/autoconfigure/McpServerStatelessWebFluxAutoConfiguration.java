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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * @author Christian Tzolov
 * @author Yanming Zhou
 */
@AutoConfiguration(before = McpServerStatelessAutoConfiguration.class)
@ConditionalOnClass(McpSchema.class)
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
@Conditional({ McpServerStdioDisabledCondition.class,
		McpServerStatelessAutoConfiguration.EnabledStatelessServerCondition.class })
public class McpServerStatelessWebFluxAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebFluxStatelessServerTransport webFluxStatelessServerTransport(
			@Qualifier("mcpServerObjectMapper") ObjectMapper objectMapper,
			McpServerStreamableHttpProperties serverProperties) {

		return WebFluxStatelessServerTransport.builder()
			.jsonMapper(new JacksonMcpJsonMapper(objectMapper))
			.messageEndpoint(serverProperties.getMcpEndpoint())
			.build();
	}

	// Router function for stateless http transport used by Spring WebFlux to start an
	// HTTP server.
	@Bean
	@ConditionalOnMissingBean(name = "webFluxStatelessServerRouterFunction")
	public RouterFunction<?> webFluxStatelessServerRouterFunction(
			WebFluxStatelessServerTransport webFluxStatelessTransport) {
		return webFluxStatelessTransport.getRouterFunction();
	}

}
