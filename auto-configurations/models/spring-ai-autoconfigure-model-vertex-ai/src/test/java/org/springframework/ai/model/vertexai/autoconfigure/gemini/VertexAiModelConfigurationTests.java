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

package org.springframework.ai.model.vertexai.autoconfigure.gemini;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for OpenAI auto configurations' conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class VertexAiModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
			"spring.ai.vertex.ai.gemini.project-id=" + System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"),
			"spring.ai.vertex.ai.gemini.location=" + System.getenv("VERTEX_AI_GEMINI_LOCATION"));

	@Test
	void chatModelActivation() {

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(VertexAiGeminiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiGeminiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(VertexAiGeminiChatModel.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(VertexAiGeminiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=vertexai")
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiGeminiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VertexAiGeminiChatModel.class)).isNotEmpty();
			});
	}

}
