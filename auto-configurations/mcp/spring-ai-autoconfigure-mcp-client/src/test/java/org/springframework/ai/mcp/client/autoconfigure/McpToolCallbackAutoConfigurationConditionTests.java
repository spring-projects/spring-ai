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

package org.springframework.ai.mcp.client.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.client.autoconfigure.McpToolCallbackAutoConfiguration.McpToolCallbackAutoConfigurationCondition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpToolCallbackAutoConfigurationCondition}.
 */
public class McpToolCallbackAutoConfigurationConditionTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void matchesWhenBothPropertiesAreEnabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.toolcallback.enabled=true")
			.run(context -> assertThat(context).hasBean("testBean"));
	}

	@Test
	void doesNotMatchWhenMcpClientIsDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.toolcallback.enabled=true")
			.run(context -> assertThat(context).doesNotHaveBean("testBean"));
	}

	@Test
	void doesNotMatchWhenToolCallbackIsDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean("testBean"));
	}

	@Test
	void doesNotMatchWhenBothPropertiesAreDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean("testBean"));
	}

	@Test
	void doesMatchWhenToolCallbackPropertyIsMissing() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.enabled=true")
			.run(context -> assertThat(context).hasBean("testBean"));
	}

	@Test
	void doesMatchWhenBothPropertiesAreMissing() {
		this.contextRunner.run(context -> assertThat(context).hasBean("testBean"));
	}

	@Configuration
	@Conditional(McpToolCallbackAutoConfigurationCondition.class)
	static class TestConfiguration {

		@Bean
		String testBean() {
			return "testBean";
		}

	}

}
