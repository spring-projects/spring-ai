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
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.servlet.function.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class McpServerSseWebMvcAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerSseWebMvcAutoConfiguration.class,
				McpServerAutoConfiguration.class, McpServerObjectMapperAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebMvcSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);

			McpServerProperties serverProperties = context.getBean(McpServerProperties.class);
			assertThat(serverProperties.getSse().getBaseUrl()).isEqualTo("");
			assertThat(serverProperties.getSse().getSseEndpoint()).isEqualTo("/sse");
			assertThat(serverProperties.getSse().getSseMessageEndpoint()).isEqualTo("/mcp/message");
			assertThat(serverProperties.getSse().getKeepAliveInterval()).isNull();

		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.base-url=http://localhost:8080",
					"spring.ai.mcp.server.sse.sse-endpoint=/events",
					"spring.ai.mcp.server.sse.sse-message-endpoint=/api/mcp/message")
			.run(context -> {
				McpServerProperties sseProperties = context.getBean(McpServerProperties.class);
				assertThat(sseProperties.getSse().getBaseUrl()).isEqualTo("http://localhost:8080");
				assertThat(sseProperties.getSse().getSseEndpoint()).isEqualTo("/events");
				assertThat(sseProperties.getSse().getSseMessageEndpoint()).isEqualTo("/api/mcp/message");

				// Verify the server is configured with the endpoints
				McpSyncServer server = context.getBean(McpSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void objectMapperConfiguration() {
		this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebMvcSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void stdioEnabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcSseServerTransportProvider.class));
	}

	@Test
	void serverDisableConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(WebMvcSseServerTransportProvider.class);
			assertThat(context).doesNotHaveBean(RouterFunction.class);
		});
	}

	@Test
	void serverBaseUrlConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.sse.base-url=/test")
			.run(context -> assertThat(context.getBean(WebMvcSseServerTransportProvider.class)).extracting("baseUrl")
				.isEqualTo("/test"));
	}

	@Test
	void servletEnvironmentConfiguration() {
		new ApplicationContextRunner(() -> new AnnotationConfigApplicationContext() {
			@Override
			public ConfigurableEnvironment getEnvironment() {
				return new StandardServletEnvironment();
			}
		}).withConfiguration(AutoConfigurations.of(McpServerSseWebMvcAutoConfiguration.class,
				McpServerAutoConfiguration.class, McpServerObjectMapperAutoConfiguration.class))
			.run(context -> {
				var mcpSyncServer = context.getBean(McpSyncServer.class);
				var field = ReflectionUtils.findField(McpSyncServer.class, "immediateExecution");
				field.setAccessible(true);
				assertThat(field.getBoolean(mcpSyncServer)).isTrue();
			});
	}

	@Test
	void routerFunctionIsCreatedFromProvider() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasSingleBean(WebMvcSseServerTransportProvider.class);

			// Verify that the RouterFunction is created from the provider
			WebMvcSseServerTransportProvider serverTransport = context.getBean(WebMvcSseServerTransportProvider.class);
			RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
			assertThat(routerFunction).isNotNull().isEqualTo(serverTransport.getRouterFunction());
		});
	}

	@Test
	void routerFunctionIsCustom() {
		this.contextRunner
			.withBean("webMvcSseServerRouterFunction", RouterFunction.class, () -> mock(RouterFunction.class))
			.run(context -> {
				assertThat(context).hasSingleBean(RouterFunction.class);

				RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
				assertThat(mockingDetails(routerFunction).isMock()).isTrue();
			});
	}

}
