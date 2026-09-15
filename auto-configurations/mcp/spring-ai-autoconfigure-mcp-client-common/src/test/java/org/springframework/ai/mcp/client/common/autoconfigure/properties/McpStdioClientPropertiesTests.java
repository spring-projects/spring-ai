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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link McpStdioClientProperties}.
 */
class McpStdioClientPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void stdioConnectionWithoutArgsUsesNoArguments() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=echo")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);

				assertThatCode(properties::toServerParameters).doesNotThrowAnyException();
				assertThat(properties.toServerParameters()).containsKey("server1");
			});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(McpStdioClientProperties.class)
	static class TestConfiguration {

	}

}
