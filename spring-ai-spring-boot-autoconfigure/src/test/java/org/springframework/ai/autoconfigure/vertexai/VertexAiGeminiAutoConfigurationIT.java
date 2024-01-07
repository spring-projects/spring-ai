/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.autoconfigure.vertexai.gemini.GeminiAutoConfiguration;
import org.springframework.ai.vertex.generation.VertexAiGeminiClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VERTEX_AI_PROJECT_ID", matches = ".*")
public class VertexAiGeminiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(VertexAiGeminiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.vertex.ai.gemini.location=us-central1",
				"spring.ai.vertex.ai.gemini.projectId=" + System.getenv("VERTEX_AI_PROJECT_ID"))
		.withConfiguration(AutoConfigurations.of(GeminiAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			VertexAiGeminiClient client = context.getBean(VertexAiGeminiClient.class);

			String response = client.generate("Hello");

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

}
