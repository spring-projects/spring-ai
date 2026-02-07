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
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class McpServerSseWebFluxAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerSseWebFluxAutoConfiguration.class,
				McpServerAutoConfiguration.class, McpServerObjectMapperAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);

			McpServerSseProperties sseProperties = context.getBean(McpServerSseProperties.class);
			assertThat(sseProperties.getBaseUrl()).isEqualTo("");
			assertThat(sseProperties.getSseEndpoint()).isEqualTo("/sse");
			assertThat(sseProperties.getSseMessageEndpoint()).isEqualTo("/mcp/message");
			assertThat(sseProperties.getKeepAliveInterval()).isNull();

		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.base-url=http://localhost:8080",
					"spring.ai.mcp.server.sse-endpoint=/events",
					"spring.ai.mcp.server.sse-message-endpoint=/api/mcp/message")
			.run(context -> {
				McpServerSseProperties sseProperties = context.getBean(McpServerSseProperties.class);
				assertThat(sseProperties.getBaseUrl()).isEqualTo("http://localhost:8080");
				assertThat(sseProperties.getSseEndpoint()).isEqualTo("/events");
				assertThat(sseProperties.getSseMessageEndpoint()).isEqualTo("/api/mcp/message");

				// Verify the server is configured with the endpoints
				McpSyncServer server = context.getBean(McpSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void objectMapperConfiguration() {
		this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebFluxSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void stdioEnabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxSseServerTransportProvider.class));
	}

	@Test
	void serverDisableConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(WebFluxSseServerTransportProvider.class);
			assertThat(context).doesNotHaveBean(RouterFunction.class);
		});
	}

	@Test
	void serverBaseUrlConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.base-url=/test")
			.run(context -> assertThat(context.getBean(WebFluxSseServerTransportProvider.class)).extracting("baseUrl")
				.isEqualTo("/test"));
	}

	@Test
	void routerFunctionIsCreatedFromProvider() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasSingleBean(WebFluxSseServerTransportProvider.class);

			// Verify that the RouterFunction is created from the provider
			WebFluxSseServerTransportProvider serverTransport = context
				.getBean(WebFluxSseServerTransportProvider.class);
			RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
			assertThat(routerFunction).isNotNull().isEqualTo(serverTransport.getRouterFunction());
		});
	}

	@Test
	void routerFunctionIsCustom() {
		this.contextRunner
			.withBean("webfluxSseServerRouterFunction", RouterFunction.class, () -> mock(RouterFunction.class))
			.run(context -> {
				assertThat(context).hasSingleBean(RouterFunction.class);

				RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
				assertThat(mockingDetails(routerFunction).isMock()).isTrue();
			});
	}

}
