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

package org.springframework.ai.model.minimax.autoconfigure;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link MiniMaxConnectionProperties}, {@link MiniMaxChatProperties} and
 * {@link MiniMaxEmbeddingProperties}.
 *
 * @author Geng Rong
 * @author Issam El-atif
 */
public class MiniMaxPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.minimax.base-url=TEST_BASE_URL",
				"spring.ai.minimax.api-key=abc123",
				"spring.ai.minimax.chat.options.model=MODEL_XYZ",
				"spring.ai.minimax.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(MiniMaxChatProperties.class);
				var connectionProperties = context.getBean(MiniMaxConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.minimax.base-url=TEST_BASE_URL",
				"spring.ai.minimax.api-key=abc123",
				"spring.ai.minimax.chat.base-url=TEST_BASE_URL2",
				"spring.ai.minimax.chat.api-key=456",
				"spring.ai.minimax.chat.options.model=MODEL_XYZ",
				"spring.ai.minimax.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(MiniMaxChatProperties.class);
				var connectionProperties = context.getBean(MiniMaxConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.minimax.base-url=TEST_BASE_URL",
				"spring.ai.minimax.api-key=abc123",
				"spring.ai.minimax.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MiniMaxEmbeddingProperties.class);
				var connectionProperties = context.getBean(MiniMaxConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isNull();

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.minimax.base-url=TEST_BASE_URL",
				"spring.ai.minimax.api-key=abc123",
				"spring.ai.minimax.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.minimax.embedding.api-key=456",
				"spring.ai.minimax.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MiniMaxEmbeddingProperties.class);
				var connectionProperties = context.getBean(MiniMaxConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.minimax.api-key=API_KEY",
				"spring.ai.minimax.base-url=TEST_BASE_URL",

				"spring.ai.minimax.chat.options.model=MODEL_XYZ",
				"spring.ai.minimax.chat.options.frequencyPenalty=-1.5",
				"spring.ai.minimax.chat.options.logitBias.myTokenId=-5",
				"spring.ai.minimax.chat.options.maxTokens=123",
				"spring.ai.minimax.chat.options.n=10",
				"spring.ai.minimax.chat.options.presencePenalty=0",
				"spring.ai.minimax.chat.options.responseFormat.type=json",
				"spring.ai.minimax.chat.options.seed=66",
				"spring.ai.minimax.chat.options.stop=boza,koza",
				"spring.ai.minimax.chat.options.temperature=0.55",
				"spring.ai.minimax.chat.options.topP=0.56",

				// "spring.ai.minimax.chat.options.toolChoice.functionName=toolChoiceFunctionName",
				"spring.ai.minimax.chat.options.toolChoice=" + ModelOptionsUtils.toJsonString(MiniMaxApi.ChatCompletionRequest.ToolChoiceBuilder.function("toolChoiceFunctionName")),

				"spring.ai.minimax.chat.options.tools[0].function.name=myFunction1",
				"spring.ai.minimax.chat.options.tools[0].function.description=function description",
				"spring.ai.minimax.chat.options.tools[0].function.jsonSchema=" + """
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
					"""
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(MiniMaxChatProperties.class);
				var connectionProperties = context.getBean(MiniMaxConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getN()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getResponseFormat())
					.isEqualTo(new MiniMaxApi.ChatCompletionRequest.ResponseFormat("json"));
				assertThat(chatProperties.getOptions().getSeed()).isEqualTo(66);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);

				JSONAssert.assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
						chatProperties.getOptions().getToolChoice(), JSONCompareMode.LENIENT);

				assertThat(chatProperties.getOptions().getTools()).hasSize(1);
				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.getType()).isEqualTo(MiniMaxApi.FunctionTool.Type.FUNCTION);
				var function = tool.getFunction();
				assertThat(function.getName()).isEqualTo("myFunction1");
				assertThat(function.getDescription()).isEqualTo("function description");
				assertThat(function.getParameters()).isNotEmpty();
			});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.minimax.api-key=API_KEY",
				"spring.ai.minimax.base-url=TEST_BASE_URL",

				"spring.ai.minimax.embedding.options.model=MODEL_XYZ",
				"spring.ai.minimax.embedding.options.encodingFormat=MyEncodingFormat"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(MiniMaxConnectionProperties.class);
				var embeddingProperties = context.getBean(MiniMaxEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	void embeddingActivation() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=minimax")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=minimax")
			.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
			});

	}

}
