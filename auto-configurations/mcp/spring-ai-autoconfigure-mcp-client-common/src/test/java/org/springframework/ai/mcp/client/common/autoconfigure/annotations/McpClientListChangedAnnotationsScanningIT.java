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

package org.springframework.ai.mcp.client.common.autoconfigure.annotations;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpToolListChanged;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MCP client list-changed annotations scanning.
 *
 * <p>
 * This test validates that the annotation scanner correctly identifies and processes
 * {@code @McpToolListChanged}, {@code @McpResourceListChanged}, and
 * {@code @McpPromptListChanged} annotations.
 *
 * @author Fu Jian
 */
public class McpClientListChangedAnnotationsScanningIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class,
				McpClientSpecificationFactoryAutoConfiguration.class));

	@ParameterizedTest
	@ValueSource(strings = { "SYNC", "ASYNC" })
	void shouldScanAllThreeListChangedAnnotations(String clientType) {
		String prefix = clientType.toLowerCase();

		this.contextRunner.withUserConfiguration(AllListChangedConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=" + clientType)
			.run(context -> {
				// Verify all three annotations were scanned
				McpClientAnnotationScannerAutoConfiguration.ClientMcpAnnotatedBeans annotatedBeans = context
					.getBean(McpClientAnnotationScannerAutoConfiguration.ClientMcpAnnotatedBeans.class);
				assertThat(annotatedBeans.getBeansByAnnotation(McpToolListChanged.class)).hasSize(1);
				assertThat(annotatedBeans.getBeansByAnnotation(McpResourceListChanged.class)).hasSize(1);
				assertThat(annotatedBeans.getBeansByAnnotation(McpPromptListChanged.class)).hasSize(1);

				// Verify all three specification beans were created
				assertThat(context).hasBean(prefix + "ToolListChangedSpecs");
				assertThat(context).hasBean(prefix + "ResourceListChangedSpecs");
				assertThat(context).hasBean(prefix + "PromptListChangedSpecs");
			});
	}

	@ParameterizedTest
	@ValueSource(strings = { "SYNC", "ASYNC" })
	void shouldNotScanAnnotationsWhenScannerDisabled(String clientType) {
		String prefix = clientType.toLowerCase();

		this.contextRunner.withUserConfiguration(AllListChangedConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=" + clientType,
					"spring.ai.mcp.client.annotation-scanner.enabled=false")
			.run(context -> {
				// Verify scanner beans were not created
				assertThat(context).doesNotHaveBean(McpClientAnnotationScannerAutoConfiguration.class);
				assertThat(context).doesNotHaveBean(prefix + "ToolListChangedSpecs");
				assertThat(context).doesNotHaveBean(prefix + "ResourceListChangedSpecs");
				assertThat(context).doesNotHaveBean(prefix + "PromptListChangedSpecs");
			});
	}

	@Configuration
	static class AllListChangedConfiguration {

		@Bean
		TestListChangedHandlers testHandlers() {
			return new TestListChangedHandlers();
		}

	}

	static class TestListChangedHandlers {

		@McpToolListChanged(clients = "test-client")
		public void onToolListChanged(List<McpSchema.Tool> updatedTools) {
			// Test handler for tool list changes
		}

		@McpResourceListChanged(clients = "test-client")
		public void onResourceListChanged(List<McpSchema.Resource> updatedResources) {
			// Test handler for resource list changes
		}

		@McpPromptListChanged(clients = "test-client")
		public void onPromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
			// Test handler for prompt list changes
		}

	}

}
