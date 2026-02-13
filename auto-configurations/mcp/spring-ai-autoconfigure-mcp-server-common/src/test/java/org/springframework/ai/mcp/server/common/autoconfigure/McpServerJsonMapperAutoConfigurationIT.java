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

package org.springframework.ai.mcp.server.common.autoconfigure;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link McpServerJsonMapperAutoConfiguration}
 *
 * @author guan xu
 */
public class McpServerJsonMapperAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class));

	@Test
	void defaultMcpServerJsonMapper() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(JsonMapper.class);
			assertThat(context).hasBean("mcpServerJsonMapper");
		});
	}

	@Test
	void customizeMcpServerJsonMapper() {
		this.contextRunner.withConfiguration(UserConfigurations.of(TestConfig.class)).run(context -> {
			assertThat(context).hasSingleBean(JsonMapper.class);
			assertThat(context).hasBean("mcpServerJsonMapper");

			var mcpServerJsonMapper = context.getBean("mcpServerJsonMapper", JsonMapper.class);
			var customizedMcpServerJsonMapper = context.getBean(TestConfig.class).mcpServerJsonMapper();
			assertThat(customizedMcpServerJsonMapper).isSameAs(mcpServerJsonMapper);
		});
	}

	@Configuration
	static class TestConfig {

		@Bean(name = "mcpServerJsonMapper")
		JsonMapper mcpServerJsonMapper() {
			return new JsonMapper();
		}

	}

}
