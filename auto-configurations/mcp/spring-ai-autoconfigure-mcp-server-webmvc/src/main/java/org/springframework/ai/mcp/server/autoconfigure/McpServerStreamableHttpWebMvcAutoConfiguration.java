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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
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

	/**
	 * Creates a WebMvc streamable server transport provider.
	 * @param objectMapperProvider the object mapper provider
	 * @param serverProperties the server properties
	 * @return the transport provider
	 */
	@Bean
	@ConditionalOnMissingBean
	public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
			final ObjectProvider<ObjectMapper> objectMapperProvider,
			final McpServerStreamableHttpProperties serverProperties) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		return WebMvcStreamableServerTransportProvider.builder()
			.jsonMapper(new JacksonMcpJsonMapper(objectMapper))
			.mcpEndpoint(serverProperties.getMcpEndpoint())
			.keepAliveInterval(serverProperties.getKeepAliveInterval())
			.disallowDelete(serverProperties.isDisallowDelete())
			.contextExtractor(this::extractContextFromRequest)
			.build();
	}

	private McpTransportContext extractContextFromRequest(final ServerRequest serverRequest) {
		Map<String, Object> headersMap = new HashMap<>();
		serverRequest.headers().asHttpHeaders().forEach((headerName, headerValues) -> {
			if (!headerValues.isEmpty()) {
				headersMap.put(headerName, headerValues.get(0));
			}
		});
		return McpTransportContext.create(headersMap);
	}

	/**
	 * Creates a router function for the streamable server transport.
	 * @param webMvcProvider the transport provider
	 * @return the router function
	 */
	@Bean
	@ConditionalOnMissingBean(name = "webMvcStreamableServerRouterFunction")
	public RouterFunction<ServerResponse> webMvcStreamableServerRouterFunction(
			final WebMvcStreamableServerTransportProvider webMvcProvider) {
		return webMvcProvider.getRouterFunction();
	}

}
