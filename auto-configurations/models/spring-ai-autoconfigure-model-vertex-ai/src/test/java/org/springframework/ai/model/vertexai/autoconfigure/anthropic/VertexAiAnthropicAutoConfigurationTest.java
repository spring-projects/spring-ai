/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.vertexai.autoconfigure.anthropic;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.ai.vertexai.anthropic.api.VertexAiAnthropicApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VertexAiAnthropicChatAutoConfiguration}.
 */
public class VertexAiAnthropicAutoConfigurationTest {

	ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.vertex.ai.anthropic.project-id=dummy_pj",
				"spring.ai.vertex.ai.anthropic.location=asia-east1",
				"spring.ai.vertex.ai.anthropic.chat.options.model=claude-sonnet-4@20250514")
		.withConfiguration(SpringAiTestAutoConfigurations.of(VertexAiAnthropicChatAutoConfiguration.class));

	/**
	 * Tests that the Vertex AI Anthropic API and chat model beans are properly
	 * configured.
	 */
	@Test
	void testChatModelConfiguration() {
		this.contextRunner.run(context -> {
			var api = context.getBean(VertexAiAnthropicApi.class);
			assertThat(api).isNotNull();

			var chatModel = context.getBean(AnthropicChatModel.class);
			assertThat(chatModel).isNotNull();
		});
	}

}
