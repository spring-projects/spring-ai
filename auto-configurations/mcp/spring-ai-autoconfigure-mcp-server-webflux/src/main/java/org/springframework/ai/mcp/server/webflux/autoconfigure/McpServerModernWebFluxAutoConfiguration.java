/*
 * Copyright 2023-present the original author or authors.
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

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerModernProperties;
import org.springframework.ai.mcp.server.webflux.transport.modern.WebFluxMcpTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * {@link AutoConfiguration Auto-configuration} of the WebFlux transport for the
 * 2026-07-28 ("modern") MCP server. Active when
 * {@code spring.ai.mcp.server.protocol=MODERN} and stdio is disabled.
 * <p>
 * A {@link ServerHttpHeaderValidator} bean, when present, validates Origin and Host
 * headers. A {@code McpTransportContextExtractor<ServerRequest>} bean, when present,
 * derives the transport context from the request; otherwise
 * {@link HeaderTransportContextExtractor} exposes the request headers.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
@AutoConfiguration(after = { McpServerModernAutoConfiguration.class, McpServerJsonMapperAutoConfiguration.class })
@ConditionalOnClass({ McpServer.class, WebFluxMcpTransport.class })
@ConditionalOnBean(McpServer.class)
@EnableConfigurationProperties(McpServerModernProperties.class)
@Conditional({ McpServerStdioDisabledCondition.class,
		McpServerModernAutoConfiguration.EnabledModernServerCondition.class })
public class McpServerModernWebFluxAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebFluxMcpTransport webFluxMcpTransport(McpServer mcpServer,
			@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, McpServerModernProperties modernProperties,
			ObjectProvider<ServerHttpHeaderValidator> headerValidator,
			ObjectProvider<McpTransportContextExtractor<ServerRequest>> contextExtractor) {
		WebFluxMcpTransport.Builder builder = WebFluxMcpTransport.builder(mcpServer)
			.jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
			.endpoint(modernProperties.getMcpEndpoint())
			.keepAliveInterval(modernProperties.effectiveKeepAliveInterval())
			.maxRequestSize((int) Math.min(Integer.MAX_VALUE, modernProperties.getMaxRequestSize().toBytes()));
		headerValidator.ifAvailable(builder::httpHeaderValidator);
		builder.contextExtractor(contextExtractor.getIfAvailable(HeaderTransportContextExtractor::new));
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean(name = "webFluxModernServerRouterFunction")
	public RouterFunction<?> webFluxModernServerRouterFunction(WebFluxMcpTransport webFluxMcpTransport) {
		return webFluxMcpTransport.getRouterFunction();
	}

	@Bean
	public SmartLifecycle webFluxMcpTransportLifecycle(WebFluxMcpTransport webFluxMcpTransport) {
		return new ModernTransportLifecycle(webFluxMcpTransport);
	}

	/**
	 * Closes the transport, and with it the open {@code subscriptions/listen} streams,
	 * when the application shuts down.
	 */
	static final class ModernTransportLifecycle implements SmartLifecycle {

		private final WebFluxMcpTransport transport;

		private volatile boolean running;

		ModernTransportLifecycle(WebFluxMcpTransport transport) {
			this.transport = transport;
		}

		@Override
		public void start() {
			this.running = true;
		}

		@Override
		public void stop() {
			if (this.running) {
				this.running = false;
				this.transport.closeGracefully();
			}
		}

		@Override
		public boolean isRunning() {
			return this.running;
		}

	}

}
