/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.dashscope;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.dashscope.DashscopeChatModel;
import org.springframework.ai.dashscope.DashscopeEmbeddingModel;
import org.springframework.ai.dashscope.api.DashscopeApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link DashscopeConnectionProperties}, {@link DashscopeChatProperties}
 * and {@link DashscopeEmbeddingProperties}.
 *
 * @author Nottyjay
 * @since 1.0.0
 */
public class DashscopePropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.dashscope.base-url=TEST_BASE_URL",
				"spring.ai.dashscope.api-key=abc123",
				"spring.ai.dashscope.chat.options.model=MODEL_XYZ",
				"spring.ai.dashscope.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DashscopeChatProperties.class);
				var connectionProperties = context.getBean(DashscopeConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.dashscope.base-url=TEST_BASE_URL",
				"spring.ai.dashscope.api-key=abc123",
				"spring.ai.dashscope.chat.base-url=TEST_BASE_URL2",
				"spring.ai.dashscope.chat.api-key=456",
				"spring.ai.dashscope.chat.options.model=MODEL_XYZ",
				"spring.ai.dashscope.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DashscopeChatProperties.class);
				var connectionProperties = context.getBean(DashscopeConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.dashscope.base-url=TEST_BASE_URL",
				"spring.ai.dashscope.api-key=abc123",
				"spring.ai.dashscope.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(DashscopeEmbeddingProperties.class);
				var connectionProperties = context.getBean(DashscopeConnectionProperties.class);

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
				"spring.ai.dashscope.base-url=TEST_BASE_URL",
				"spring.ai.dashscope.api-key=abc123",
				"spring.ai.dashscope.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.dashscope.embedding.api-key=456",
				"spring.ai.dashscope.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(DashscopeEmbeddingProperties.class);
				var connectionProperties = context.getBean(DashscopeConnectionProperties.class);

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
				"spring.ai.dashscope.api-key=API_KEY",
				"spring.ai.dashscope.base-url=TEST_BASE_URL",

				"spring.ai.dashscope.chat.options.model=MODEL_XYZ",
				"spring.ai.dashscope.chat.options.stream=true",
				"spring.ai.dashscope.chat.options.logitBias.myTokenId=-5",
				"spring.ai.dashscope.chat.options.maxTokens=123",
				"spring.ai.dashscope.chat.options.topK=10",
										"spring.ai.dashscope.chat.options.enableSearch=true",
				"spring.ai.dashscope.chat.options.repetitionPenalty=0",
				"spring.ai.dashscope.chat.options.stop=boza,koza",
				"spring.ai.dashscope.chat.options.temperature=0.55",
				"spring.ai.dashscope.chat.options.topP=0.56",
										"spring.ai.dashscope.chat.options.resultFormat=message",

				// "spring.ai.openai.chat.options.toolChoice.functionName=toolChoiceFunctionName",
//				"spring.ai.dashscope.chat.options.toolChoice=" + ModelOptionsUtils.toJsonString(ToolChoiceBuilder.FUNCTION("toolChoiceFunctionName")),

				"spring.ai.dashscope.chat.options.tools[0].function.name=myFunction1",
				"spring.ai.dashscope.chat.options.tools[0].function.description=function description",
				"spring.ai.dashscope.chat.options.tools[0].function.jsonSchema=" + """
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
					"spring.ai.dashscope.chat.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DashscopeChatProperties.class);
				var connectionProperties = context.getBean(DashscopeConnectionProperties.class);
				var embeddingProperties = context.getBean(DashscopeEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("text-embedding-v1");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getStream()).isTrue();
				assertThat(chatProperties.getOptions().getTopK()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getRepetitionPenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getEnableSearch()).isTrue();
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56f);
				assertThat(chatProperties.getOptions().getResultFormat()).isEqualTo("message");

				assertThat(chatProperties.getOptions().getTools()).hasSize(1);
				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.type()).isEqualTo(DashscopeApi.FunctionTool.Type.FUNCTION);
				var function = tool.function();
				assertThat(function.name()).isEqualTo("myFunction1");
				assertThat(function.description()).isEqualTo("function description");
				assertThat(function.parameters()).isNotEmpty();
			});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.dashscope.api-key=API_KEY",
				"spring.ai.dashscope.base-url=TEST_BASE_URL",

				"spring.ai.dashscope.embedding.options.model=MODEL_XYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DashscopeConnectionProperties.class);
				var embeddingProperties = context.getBean(DashscopeEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	void embeddingActivation() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.dashscope.api-key=API_KEY", "spring.ai.dashscope.base-url=TEST_BASE_URL",
					"spring.ai.dashscope.embedding.enabled=false")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DashscopeEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DashscopeEmbeddingModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.dashscope.api-key=API_KEY", "spring.ai.dashscope.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DashscopeEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DashscopeEmbeddingModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.dashscope.api-key=API_KEY", "spring.ai.dashscope.base-url=TEST_BASE_URL",
					"spring.ai.dashscope.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DashscopeEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DashscopeEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.dashscope.api-key=API_KEY", "spring.ai.dashscope.base-url=TEST_BASE_URL",
					"spring.ai.dashscope.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DashscopeChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DashscopeChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.dashscope.api-key=API_KEY", "spring.ai.dashscope.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DashscopeChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DashscopeChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.dashscope.api-key=API_KEY", "spring.ai.dashscope.base-url=TEST_BASE_URL",
					"spring.ai.dashscope.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DashscopeChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DashscopeChatModel.class)).isNotEmpty();
			});

	}

}
