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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link McpServerObjectMapperAutoConfiguration}
 *
 * @author guan xu
 */
public class McpServerObjectMapperAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerObjectMapperAutoConfiguration.class));

	@Test
	void defaultMcpServerObjectMapper() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ObjectMapper.class);
			assertThat(context).hasBean("mcpServerObjectMapper");
		});
	}

	@Test
	void customizeMcpServerObjectMapper() {
		this.contextRunner.withConfiguration(UserConfigurations.of(TestConfig.class)).run(context -> {
			assertThat(context).hasSingleBean(ObjectMapper.class);
			assertThat(context).hasBean("mcpServerObjectMapper");

			var mcpServerObjectMapper = context.getBean("mcpServerObjectMapper", ObjectMapper.class);
			var customizedMcpServerObjectMapper = context.getBean(TestConfig.class).mcpServerObjectMapper();
			assertThat(customizedMcpServerObjectMapper).isSameAs(mcpServerObjectMapper);
		});
	}

	@Configuration
	static class TestConfig {

		@Bean(name = "mcpServerObjectMapper")
		ObjectMapper mcpServerObjectMapper() {
			return new ObjectMapper();
		}

	}

}
