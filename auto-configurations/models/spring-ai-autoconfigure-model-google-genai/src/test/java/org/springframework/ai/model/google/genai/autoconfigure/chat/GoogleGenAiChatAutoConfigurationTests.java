/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class GoogleGenAiChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

	@Test
	void shouldNotFailOnAmbiguousConfigurationButPrioritizeApiKey() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=test-key",
					"spring.ai.google.genai.project-id=test-project", "spring.ai.google.genai.location=us-central1")
			.run(context -> assertThat(context).hasSingleBean(Client.class));
	}

	@Test
	void shouldFailWhenVertexAiEnabledButConfigMissing() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.vertex-ai=true")
			// Explicitly enabled but no project/location
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Vertex AI mode requires both 'project-id' and 'location' to be configured.");
			});
	}

	@Test
	void shouldConfigureVertexAiSuccessfully() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.project-id=my-project",
					"spring.ai.google.genai.location=us-central1")
			.run(context -> assertThat(context).hasSingleBean(Client.class));
	}

	@Test
	void shouldConfigureApiKeySuccessfully() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=my-gemini-key")
			.run(context -> assertThat(context).hasSingleBean(Client.class));
	}

}
