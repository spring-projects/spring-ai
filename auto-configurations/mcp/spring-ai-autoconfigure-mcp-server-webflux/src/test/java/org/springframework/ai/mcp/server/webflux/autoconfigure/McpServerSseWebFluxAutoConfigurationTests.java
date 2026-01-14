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

import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerSseWebFluxAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerSseWebFluxAutoConfiguration.class,
				McpServerJsonMapperAutoConfiguration.class, TestConfiguration.class));

	@Test
	void shouldConfigureWebFluxTransportWithCustomJsonMapper() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasSingleBean(McpServerProperties.class);

			JsonMapper jsonMapper = context.getBean("mcpServerJsonMapper", JsonMapper.class);

			// Verify that the JsonMapper is configured to ignore unknown properties

			assertThat(jsonMapper.isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
				.isFalse();

			// Test with a JSON payload containing unknown fields
			// CHECKSTYLE:OFF
			String jsonWithUnknownField = """
					{
					    "tools": ["tool1", "tool2"],
					    "name": "test",
					    "unknownField": "value"
					}
					""";
			// CHECKSTYLE:ON

			// This should not throw an exception
			TestMessage message = jsonMapper.readValue(jsonWithUnknownField, TestMessage.class);
			assertThat(message.getName()).isEqualTo("test");
		});
	}

	// Test configuration to enable McpServerProperties
	@Configuration
	@EnableConfigurationProperties(McpServerProperties.class)
	static class TestConfiguration {

	}

	// Test class to simulate the actual message structure
	static class TestMessage {

		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
