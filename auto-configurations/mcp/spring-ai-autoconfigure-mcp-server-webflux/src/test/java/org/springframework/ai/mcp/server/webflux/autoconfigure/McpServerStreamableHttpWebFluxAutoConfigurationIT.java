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

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class McpServerStreamableHttpWebFluxAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE")
		.withConfiguration(AutoConfigurations.of(McpServerStreamableHttpWebFluxAutoConfiguration.class,
				McpServerJsonMapperAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void jsonMapperConfiguration() {
		this.contextRunner.withBean(JsonMapper.class, JsonMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void serverDisableConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).doesNotHaveBean(RouterFunction.class);
		});
	}

	@Test
	void serverBaseUrlConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.mcpEndpoint=/test")
			.run(context -> assertThat(context.getBean(WebFluxStreamableServerTransportProvider.class))
				.extracting("mcpEndpoint")
				.isEqualTo("/test"));
	}

	@Test
	void keepAliveIntervalConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.keep-alive-interval=PT30S")
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void disallowDeleteConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.disallow-delete=true")
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void disallowDeleteFalseConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.disallow-delete=false")
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void customJsonMapperIsUsed() {
		JsonMapper customJsonMapper = new JsonMapper();
		this.contextRunner.withBean("customJsonMapper", JsonMapper.class, () -> customJsonMapper).run(context -> {
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			// Verify the custom JsonMapper is used
			assertThat(context.getBean(JsonMapper.class)).isSameAs(customJsonMapper);
		});
	}

	@Test
	void conditionalOnClassPresent() {
		this.contextRunner.run(context -> {
			// Verify that the configuration is loaded when required classes are present
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void conditionalOnMissingBeanWorks() {
		// Test that @ConditionalOnMissingBean works by providing a custom bean
		this.contextRunner
			.withBean("customWebFluxProvider", WebFluxStreamableServerTransportProvider.class,
					() -> WebFluxStreamableServerTransportProvider.builder()
						.jsonMapper(new JacksonMcpJsonMapper(new JsonMapper()))
						.messageEndpoint("/custom")
						.build())
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
				// Should use the custom bean, not create a new one
				WebFluxStreamableServerTransportProvider provider = context
					.getBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(provider).extracting("mcpEndpoint").isEqualTo("/custom");
			});
	}

	@Test
	void routerFunctionIsCreatedFromProvider() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);

			// Verify that the RouterFunction is created from the provider
			WebFluxStreamableServerTransportProvider serverTransport = context
				.getBean(WebFluxStreamableServerTransportProvider.class);
			RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
			assertThat(routerFunction).isNotNull().isEqualTo(serverTransport.getRouterFunction());
		});
	}

	@Test
	void routerFunctionIsCustom() {
		this.contextRunner
			.withBean("webFluxStreamableServerRouterFunction", RouterFunction.class, () -> mock(RouterFunction.class))
			.run(context -> {
				assertThat(context).hasSingleBean(RouterFunction.class);

				RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
				assertThat(mockingDetails(routerFunction).isMock()).isTrue();
			});
	}

	@Test
	void allPropertiesConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.streamable-http.mcpEndpoint=/custom-endpoint",
					"spring.ai.mcp.server.streamable-http.keep-alive-interval=PT45S",
					"spring.ai.mcp.server.streamable-http.disallow-delete=true")
			.run(context -> {
				WebFluxStreamableServerTransportProvider provider = context
					.getBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(provider).extracting("mcpEndpoint").isEqualTo("/custom-endpoint");
				// Verify beans are created successfully with all properties
				assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void enabledPropertyDefaultsToTrue() {
		// Test that when enabled property is not set, it defaults to true (matchIfMissing
		// = true)
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void enabledPropertyExplicitlyTrue() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

}
