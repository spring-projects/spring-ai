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

package org.springframework.ai.model.anthropic.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Vertex AI backend auto-configuration selection logic.
 *
 * @author dragonfsky
 */
class AnthropicVertexChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AnthropicChatAutoConfiguration.class,
				AnthropicVertexChatAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	// --- Happy path: default backend selection ---

	@Test
	void defaultBackendCreatesDirectModel() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.api-key=test-key")
			.run(context -> assertThat(context).hasSingleBean(AnthropicChatModel.class));
	}

	@Test
	void explicitDirectBackendCreatesModel() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=anthropic", "spring.ai.anthropic.api-key=test-key")
			.run(context -> assertThat(context).hasSingleBean(AnthropicChatModel.class));
	}

	// --- Vertex backend: classpath-conditional ---

	@Test
	void vertexAutoConfigurationBacksOffWhenVertexClassesAreMissing() {
		new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("com.anthropic.vertex"))
			.withConfiguration(AutoConfigurations.of(AnthropicVertexChatAutoConfiguration.class,
					ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.vertex.project-id=test-project", "spring.ai.anthropic.vertex.location=global")
			.run(context -> assertThat(context).doesNotHaveBean(AnthropicChatModel.class));
	}

	@Test
	void vertexBackendWithAnotherChatModelDoesNotCreateModel() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.vertex.project-id=test-project", "spring.ai.anthropic.vertex.location=global")
			.run(context -> assertThat(context).doesNotHaveBean(AnthropicChatModel.class));
	}

	// --- Fail-fast: conflicting properties under Vertex ---

	@Test
	void vertexBackendWithApiKeyFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai", "spring.ai.anthropic.api-key=test-key")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("api-key");
			});
	}

	@Test
	void vertexBackendWithBaseUrlFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.base-url=https://example.com")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("base-url");
			});
	}

	@Test
	void vertexBackendWithForbiddenAuthorizationHeaderFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.custom-headers.Authorization=Bearer xyz")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("Authorization");
			});
	}

	@Test
	void vertexBackendWithForbiddenApiKeyHeaderFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.custom-headers.x-api-key=sk-test")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("x-api-key");
			});
	}

	@Test
	void vertexBackendWithForbiddenAnthropicVersionHeaderFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.custom-headers.anthropic-version=2023-06-01")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("anthropic-version");
			});
	}

	@Test
	void vertexBackendPartialConfigProjectOnlyFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai",
					"spring.ai.anthropic.vertex.project-id=test-project")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("together");
			});
	}

	@Test
	void vertexBackendPartialConfigLocationOnlyFails() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai", "spring.ai.anthropic.vertex.location=us-east5")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("together");
			});
	}

	// --- User-provided bean override ---

	@Test
	void userProvidedBeanBacksOff() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.backend=vertex-ai", "spring.ai.anthropic.vertex.project-id=test",
					"spring.ai.anthropic.vertex.location=global")
			.withBean(AnthropicChatModel.class, () -> AnthropicChatModel.builder().build())
			.run(context -> assertThat(context).hasSingleBean(AnthropicChatModel.class));
	}

	// --- Direct backend: model selector ---

	@Test
	void directBackendWithOtherModelDoesNotCreateBean() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.anthropic.backend=anthropic",
					"spring.ai.anthropic.api-key=test-key")
			.run(context -> assertThat(context).doesNotHaveBean(AnthropicChatModel.class));
	}

}
