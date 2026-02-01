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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Tests for {@link MistralAiCommonProperties}, {@link MistralAiEmbeddingProperties}.
 */
class MistralAiPropertiesTests {

	private static final MistralAiCommonProperties COMMON_WITH_PROPERTIES = createCommonPropertiesWithDefaultValues();

	private static final MistralAiCommonProperties COMMON_WITHOUT_PROPERTIES = createCommonPropertiesWithoutDefaultValues();

	private static final String COMMON_API_KEY = "common_api_key";

	private static final String SPECIFIC_API_KEY = "specific_api_key";

	private static final String SPECIFIC_BASE_URL = "https://my-mistral-ai-api.com";

	@Test
	void embeddingProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL", "spring.ai.mistralai.api-key=abc123",
					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var commonProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(commonProperties.getApiKey()).isEqualTo("abc123");
				assertThat(commonProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo(MistralAiCommonProperties.DEFAULT_BASE_URL);

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	void chatOptionsTest() {
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
	void embeddingOverrideConnectionProperties() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL",
				"spring.ai.mistralai.api-key=abc123", "spring.ai.mistralai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.mistralai.embedding.api-key=456", "spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var commonProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(commonProperties.getApiKey()).isEqualTo("abc123");
				assertThat(commonProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	void embeddingOptionsTest() {
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
	void moderationOptionsTest() {
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

	@Test
	void commonPropertiesApiKeyDefaulting() {
		assertThatThrownBy(() -> new MistralAiCommonProperties().getApiKeyOrDefaultFrom(COMMON_WITH_PROPERTIES))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessage("Mistral AI common properties can't be defaulted!");
	}

	@Test
	void commonPropertiesBaseUrlDefaulting() {
		assertThatThrownBy(() -> new MistralAiCommonProperties().getBaseUrlOrDefaultFrom(COMMON_WITH_PROPERTIES))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessage("Mistral AI common properties can't be defaulted!");
	}

	static List<? extends MistralAiParentProperties> properties() {
		return List.of(createChatProperties(SPECIFIC_API_KEY, SPECIFIC_BASE_URL),
				createEmbeddingProperties(SPECIFIC_API_KEY, SPECIFIC_BASE_URL),
				createModerationProperties(SPECIFIC_API_KEY, SPECIFIC_BASE_URL),
				createOcrProperties(SPECIFIC_API_KEY, SPECIFIC_BASE_URL));
	}

	static List<? extends MistralAiParentProperties> defaultedProperties() {
		return List.of(createChatProperties(null, null), createEmbeddingProperties(null, null),
				createModerationProperties(null, null), createOcrProperties(null, null));
	}

	@ParameterizedTest
	@MethodSource("properties")
	void propertiesApiKey(MistralAiParentProperties properties) {
		assertThat(properties.getApiKeyOrDefaultFrom(COMMON_WITH_PROPERTIES)).isEqualTo(SPECIFIC_API_KEY);
	}

	@ParameterizedTest
	@MethodSource("properties")
	void propertiesBaseUrl(MistralAiParentProperties properties) {
		assertThat(properties.getBaseUrlOrDefaultFrom(COMMON_WITH_PROPERTIES)).isEqualTo(SPECIFIC_BASE_URL);
	}

	@ParameterizedTest
	@MethodSource("defaultedProperties")
	void propertiesApiKeyDefaulting(MistralAiParentProperties properties) {
		assertThat(properties.getApiKeyOrDefaultFrom(COMMON_WITH_PROPERTIES)).isEqualTo(COMMON_API_KEY);
	}

	@ParameterizedTest
	@MethodSource("defaultedProperties")
	void propertiesBaseUrlDefaulting(MistralAiParentProperties properties) {
		assertThat(properties.getBaseUrlOrDefaultFrom(COMMON_WITH_PROPERTIES))
			.isEqualTo(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	@ParameterizedTest
	@MethodSource("defaultedProperties")
	void propertiesMissingApiKey(MistralAiParentProperties properties) {
		assertThatThrownBy(() -> properties.getApiKeyOrDefaultFrom(COMMON_WITHOUT_PROPERTIES))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Mistral AI API key must be set!");
	}

	@ParameterizedTest
	@MethodSource("defaultedProperties")
	void propertiesMissingBaseUrl(MistralAiParentProperties properties) {
		assertThatThrownBy(() -> properties.getBaseUrlOrDefaultFrom(COMMON_WITHOUT_PROPERTIES))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Mistral AI base URL must be set!");
	}

	private static MistralAiCommonProperties createCommonPropertiesWithoutDefaultValues() {
		var commonProperties = new MistralAiCommonProperties();
		commonProperties.setBaseUrl(null);

		return commonProperties;
	}

	private static MistralAiCommonProperties createCommonPropertiesWithDefaultValues() {
		var commonProperties = new MistralAiCommonProperties();
		commonProperties.setApiKey(COMMON_API_KEY);

		return commonProperties;
	}

	private static MistralAiChatProperties createChatProperties(String apiKey, String baseUrl) {
		var chatProperties = new MistralAiChatProperties();
		chatProperties.setApiKey(apiKey);
		chatProperties.setBaseUrl(baseUrl);

		return chatProperties;
	}

	private static MistralAiEmbeddingProperties createEmbeddingProperties(String apiKey, String baseUrl) {
		var embeddingProperties = new MistralAiEmbeddingProperties();
		embeddingProperties.setApiKey(apiKey);
		embeddingProperties.setBaseUrl(baseUrl);

		return embeddingProperties;
	}

	private static MistralAiModerationProperties createModerationProperties(String apiKey, String baseUrl) {
		var moderationProperties = new MistralAiModerationProperties();
		moderationProperties.setApiKey(apiKey);
		moderationProperties.setBaseUrl(baseUrl);

		return moderationProperties;
	}

	private static MistralAiOcrProperties createOcrProperties(String apiKey, String baseUrl) {
		var ocrProperties = new MistralAiOcrProperties();
		ocrProperties.setApiKey(apiKey);
		ocrProperties.setBaseUrl(baseUrl);

		return ocrProperties;
	}

}
