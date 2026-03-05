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

package org.springframework.ai.mcp.server.webmvc.autoconfigure;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * @author Christian Tzolov
 * @author Yanming Zhou
 */
@AutoConfiguration(before = McpServerAutoConfiguration.class)
@ConditionalOnClass(McpSchema.class)
@EnableConfigurationProperties({ McpServerProperties.class, McpServerStreamableHttpProperties.class })
@Conditional({ McpServerStdioDisabledCondition.class,
		McpServerAutoConfiguration.EnabledStreamableServerCondition.class })
public class McpServerStreamableHttpWebMvcAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
			@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
			McpServerStreamableHttpProperties serverProperties) {

		return WebMvcStreamableServerTransportProvider.builder()
			.jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
			.mcpEndpoint(serverProperties.getMcpEndpoint())
			.keepAliveInterval(serverProperties.getKeepAliveInterval())
			.disallowDelete(serverProperties.isDisallowDelete())
			.build();
	}

	// Router function for streamable http transport used by Spring WebFlux to start an
	// HTTP server.
	@Bean
	@ConditionalOnMissingBean(name = "webMvcStreamableServerRouterFunction")
	public RouterFunction<ServerResponse> webMvcStreamableServerRouterFunction(
			WebMvcStreamableServerTransportProvider webMvcProvider) {
		return webMvcProvider.getRouterFunction();
	}

}
