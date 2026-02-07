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
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class McpServerStatelessWebFluxAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
		.withConfiguration(AutoConfigurations.of(McpServerStatelessWebFluxAutoConfiguration.class,
				McpServerObjectMapperAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void objectMapperConfiguration() {
		this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void serverDisableConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(WebFluxStatelessServerTransport.class);
			assertThat(context).doesNotHaveBean(RouterFunction.class);
		});
	}

	@Test
	void serverBaseUrlConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.mcpEndpoint=/test")
			.run(context -> assertThat(context.getBean(WebFluxStatelessServerTransport.class)).extracting("mcpEndpoint")
				.isEqualTo("/test"));
	}

	@Test
	void keepAliveIntervalConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.keep-alive-interval=PT30S")
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void disallowDeleteConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.disallow-delete=true")
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void disallowDeleteFalseConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable-http.disallow-delete=false")
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void customObjectMapperIsUsed() {
		ObjectMapper customObjectMapper = new ObjectMapper();
		this.contextRunner.withBean("customObjectMapper", ObjectMapper.class, () -> customObjectMapper).run(context -> {
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			// Verify the custom ObjectMapper is used
			assertThat(context.getBean(ObjectMapper.class)).isSameAs(customObjectMapper);
		});
	}

	@Test
	void conditionalOnClassPresent() {
		this.contextRunner.run(context -> {
			// Verify that the configuration is loaded when required classes are present
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void conditionalOnMissingBeanWorks() {
		// Test that @ConditionalOnMissingBean works by providing a custom bean
		this.contextRunner
			.withBean("customWebFluxProvider", WebFluxStatelessServerTransport.class,
					() -> WebFluxStatelessServerTransport.builder()
						.jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
						.messageEndpoint("/custom")
						.build())
			.run(context -> {
				assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
				// Should use the custom bean, not create a new one
				WebFluxStatelessServerTransport provider = context.getBean(WebFluxStatelessServerTransport.class);
				assertThat(provider).extracting("mcpEndpoint").isEqualTo("/custom");
			});
	}

	@Test
	void routerFunctionIsCreatedFromProvider() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);

			// Verify that the RouterFunction is created from the provider
			WebFluxStatelessServerTransport serverTransport = context.getBean(WebFluxStatelessServerTransport.class);
			RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
			assertThat(routerFunction).isNotNull().isEqualTo(serverTransport.getRouterFunction());
		});
	}

	@Test
	void routerFunctionIsCustom() {
		this.contextRunner
			.withBean("webFluxStatelessServerRouterFunction", RouterFunction.class, () -> mock(RouterFunction.class))
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
					"spring.ai.mcp.server.streamable-http.disallow-delete=true")
			.run(context -> {
				WebFluxStatelessServerTransport provider = context.getBean(WebFluxStatelessServerTransport.class);
				assertThat(provider).extracting("mcpEndpoint").isEqualTo("/custom-endpoint");
				// Verify beans are created successfully with all properties
				assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void enabledPropertyDefaultsToTrue() {
		// Test that when enabled property is not set, it defaults to true (matchIfMissing
		// = true)
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void enabledPropertyExplicitlyTrue() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(WebFluxStatelessServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Configuration
	private static class CustomRouterFunctionConfig {

		@Bean
		public RouterFunction<?> webFluxStatelessServerRouterFunction(
				WebFluxStatelessServerTransport webFluxStatelessTransport) {
			return mock(RouterFunction.class);
		}

	}

}
