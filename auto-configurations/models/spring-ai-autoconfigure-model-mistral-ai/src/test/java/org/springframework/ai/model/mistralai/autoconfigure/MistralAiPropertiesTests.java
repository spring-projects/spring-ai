/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link MistralAiCommonProperties}, {@link MistralAiEmbeddingProperties}.
 */
public class MistralAiPropertiesTests {

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL", "spring.ai.mistralai.api-key=abc123",
					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo(MistralAiCommonProperties.DEFAULT_BASE_URL);

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL",
				"spring.ai.mistralai.chat.options.tools[0].function.name=myFunction1",
				"spring.ai.mistralai.chat.options.tools[0].function.description=function description",
				"spring.ai.mistralai.chat.options.tools[0].function.jsonSchema=" + """
						{
							"type": "object",
							"properties": {
								"location": {
									"type": "string",
									"description": "The city and state e.g. San Francisco, CA"
								},
								"lat": {
									"type": "number",
									"description": "The city latitude"
								},
								"lon": {
									"type": "number",
									"description": "The city longitude"
								},
								"unit": {
									"type": "string",
									"enum": ["c", "f"]
								}
							},
							"required": ["location", "lat", "lon", "unit"]
						}
						""",

				"spring.ai.mistralai.api-key=abc123", "spring.ai.mistralai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.mistralai.embedding.api-key=456", "spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiChatAutoConfiguration.class))
			.run(context -> {

				var chatProperties = context.getBean(MistralAiChatProperties.class);

				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.getType()).isEqualTo(MistralAiApi.FunctionTool.Type.FUNCTION);
				var function = tool.getFunction();
				assertThat(function.getName()).isEqualTo("myFunction1");
				assertThat(function.getDescription()).isEqualTo("function description");
				assertThat(function.getParameters()).isNotEmpty();
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL",
				"spring.ai.mistralai.api-key=abc123", "spring.ai.mistralai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.mistralai.embedding.api-key=456", "spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY", "spring.ai.mistralai.base-url=TEST_BASE_URL",
					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ",
					"spring.ai.mistralai.embedding.options.encodingFormat=MyEncodingFormat")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getOptions().getEncodingFormat()).isEqualTo("MyEncodingFormat");
			});
	}

	@Test
	public void moderationOptionsTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.moderation.base-url=TEST_BASE_URL",
					"spring.ai.mistralai.moderation.api-key=abc123",
					"spring.ai.mistralai.moderation.options.model=MODERATION_MODEL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiModerationAutoConfiguration.class))
			.run(context -> {
				var moderationProperties = context.getBean(MistralAiModerationProperties.class);
				assertThat(moderationProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(moderationProperties.getApiKey()).isEqualTo("abc123");
				assertThat(moderationProperties.getOptions().getModel()).isEqualTo("MODERATION_MODEL");
			});
	}

}
