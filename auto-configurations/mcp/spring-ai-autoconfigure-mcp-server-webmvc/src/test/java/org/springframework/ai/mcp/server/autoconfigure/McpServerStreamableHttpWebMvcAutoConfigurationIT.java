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
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.function.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class McpServerStreamableHttpWebMvcAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE")
		.withConfiguration(AutoConfigurations.of(McpServerStreamableHttpWebMvcAutoConfiguration.class,
				McpServerObjectMapperAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void objectMapperConfiguration() {
		this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void serverDisableConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).doesNotHaveBean(RouterFunction.class);
		});
	}

	@Test
	void serverBaseUrlConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable.mcpEndpoint=/test")
			.run(context -> assertThat(context.getBean(WebMvcStreamableServerTransportProvider.class))
				.extracting("mcpEndpoint")
				.isEqualTo("/test"));
	}

	@Test
	void keepAliveIntervalConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable.keep-alive-interval=PT30S")
			.run(context -> {
				assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void disallowDeleteConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable.disallow-delete=true").run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void disallowDeleteFalseConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.streamable.disallow-delete=false").run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void customObjectMapperIsUsed() {
		ObjectMapper customObjectMapper = new ObjectMapper();
		this.contextRunner.withBean("customObjectMapper", ObjectMapper.class, () -> customObjectMapper).run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			// Verify the custom ObjectMapper is used
			assertThat(context.getBean(ObjectMapper.class)).isSameAs(customObjectMapper);
		});
	}

	@Test
	void conditionalOnClassPresent() {
		this.contextRunner.run(context -> {
			// Verify that the configuration is loaded when required classes are present
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void conditionalOnMissingBeanWorks() {
		// Test that @ConditionalOnMissingBean works by providing a custom bean
		this.contextRunner
			.withBean("customWebFluxProvider", WebMvcStreamableServerTransportProvider.class,
					() -> WebMvcStreamableServerTransportProvider.builder()
						.jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
						.mcpEndpoint("/custom")
						.build())
			.run(context -> {
				assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
				// Should use the custom bean, not create a new one
				WebMvcStreamableServerTransportProvider provider = context
					.getBean(WebMvcStreamableServerTransportProvider.class);
				assertThat(provider).extracting("mcpEndpoint").isEqualTo("/custom");
			});
	}

	@Test
	void routerFunctionIsCreatedFromProvider() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);

			// Verify that the RouterFunction is created from the provider
			WebMvcStreamableServerTransportProvider serverTransportProvider = context
				.getBean(WebMvcStreamableServerTransportProvider.class);
			RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
			assertThat(routerFunction).isNotNull().isEqualTo(serverTransportProvider.getRouterFunction());
		});
	}

	@Test
	void routerFunctionIsCustom() {
		this.contextRunner
			.withBean("webMvcStreamableServerRouterFunction", RouterFunction.class, () -> mock(RouterFunction.class))
			.run(context -> {
				assertThat(context).hasSingleBean(RouterFunction.class);

				RouterFunction<?> routerFunction = context.getBean(RouterFunction.class);
				assertThat(mockingDetails(routerFunction).isMock()).isTrue();
			});
	}

	@Test
	void allPropertiesConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.streamable.mcpEndpoint=/custom-endpoint",
					"spring.ai.mcp.server.streamable.keep-alive-interval=PT45S",
					"spring.ai.mcp.server.streamable.disallow-delete=true")
			.run(context -> {
				WebMvcStreamableServerTransportProvider provider = context
					.getBean(WebMvcStreamableServerTransportProvider.class);
				assertThat(provider).extracting("mcpEndpoint").isEqualTo("/custom-endpoint");
				// Verify beans are created successfully with all properties
				assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
				assertThat(context).hasSingleBean(RouterFunction.class);
			});
	}

	@Test
	void enabledPropertyDefaultsToTrue() {
		// Test that when enabled property is not set, it defaults to true (matchIfMissing
		// = true)
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void enabledPropertyExplicitlyTrue() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

}
