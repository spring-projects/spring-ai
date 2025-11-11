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

package org.springframework.ai.model.zhipuai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link ZhiPuAiConnectionProperties}, {@link ZhiPuAiChatProperties} and
 * {@link ZhiPuAiEmbeddingProperties}.
 *
 * @author Geng Rong
 * @author YunKui Lu
 * @author Issam El-atif
 */
public class ZhiPuAiPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.zhipuai.base-url=TEST_BASE_URL",
				"spring.ai.zhipuai.api-key=abc123",
				"spring.ai.zhipuai.chat.options.model=MODEL_XYZ",
				"spring.ai.zhipuai.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(ZhiPuAiChatProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

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
				"spring.ai.zhipuai.base-url=TEST_BASE_URL",
				"spring.ai.zhipuai.api-key=abc123",
				"spring.ai.zhipuai.chat.base-url=TEST_BASE_URL2",
				"spring.ai.zhipuai.chat.api-key=456",
				"spring.ai.zhipuai.chat.options.model=MODEL_XYZ",
				"spring.ai.zhipuai.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(ZhiPuAiChatProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

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
				"spring.ai.zhipuai.base-url=TEST_BASE_URL",
				"spring.ai.zhipuai.api-key=abc123",
				"spring.ai.zhipuai.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(ZhiPuAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

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
				"spring.ai.zhipuai.base-url=TEST_BASE_URL",
				"spring.ai.zhipuai.api-key=abc123",
				"spring.ai.zhipuai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.zhipuai.embedding.api-key=456",
				"spring.ai.zhipuai.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(ZhiPuAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void imageProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.zhipuai.base-url=TEST_BASE_URL",
						"spring.ai.zhipuai.api-key=abc123",
						"spring.ai.zhipuai.image.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(ZhiPuAiImageProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(imageProperties.getApiKey()).isNull();
				assertThat(imageProperties.getBaseUrl()).isNull();

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void imageOverrideConnectionProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.zhipuai.base-url=TEST_BASE_URL",
						"spring.ai.zhipuai.api-key=abc123",
						"spring.ai.zhipuai.image.base-url=TEST_BASE_URL2",
						"spring.ai.zhipuai.image.api-key=456",
						"spring.ai.zhipuai.image.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(ZhiPuAiImageProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(imageProperties.getApiKey()).isEqualTo("456");
				assertThat(imageProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.zhipuai.api-key=API_KEY",
				"spring.ai.zhipuai.base-url=TEST_BASE_URL",

				"spring.ai.zhipuai.chat.options.model=MODEL_XYZ",
				"spring.ai.zhipuai.chat.options.maxTokens=123",
				"spring.ai.zhipuai.chat.options.stop=boza,koza",
				"spring.ai.zhipuai.chat.options.temperature=0.55",
				"spring.ai.zhipuai.chat.options.topP=0.56",
				"spring.ai.zhipuai.chat.options.requestId=RequestId",
				"spring.ai.zhipuai.chat.options.doSample=true",

				// "spring.ai.zhipuai.chat.options.toolChoice.functionName=toolChoiceFunctionName",
				"spring.ai.zhipuai.chat.options.toolChoice=" + ModelOptionsUtils.toJsonString(ZhiPuAiApi.ChatCompletionRequest.ToolChoiceBuilder.function("toolChoiceFunctionName")),

				"spring.ai.zhipuai.chat.options.tools[0].function.name=myFunction1",
				"spring.ai.zhipuai.chat.options.tools[0].function.description=function description",
				"spring.ai.zhipuai.chat.options.tools[0].function.jsonSchema=" + """
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
					"spring.ai.zhipuai.chat.options.user=userXYZ",
					"spring.ai.zhipuai.chat.options.response-format.type=json_object",
					"spring.ai.zhipuai.chat.options.thinking.type=disabled"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(ZhiPuAiChatProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getOptions().getRequestId()).isEqualTo("RequestId");
				assertThat(chatProperties.getOptions().getDoSample()).isEqualTo(Boolean.TRUE);
				assertThat(chatProperties.getOptions().getResponseFormat().type()).isEqualTo("json_object");
				assertThat(chatProperties.getOptions().getThinking().type()).isEqualTo("disabled");

				JSONAssert.assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
						chatProperties.getOptions().getToolChoice(), JSONCompareMode.LENIENT);

				assertThat(chatProperties.getOptions().getUser()).isEqualTo("userXYZ");

				assertThat(chatProperties.getOptions().getTools()).hasSize(1);
				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.getType()).isEqualTo(ZhiPuAiApi.FunctionTool.Type.FUNCTION);
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
				"spring.ai.zhipuai.api-key=API_KEY",
				"spring.ai.zhipuai.base-url=TEST_BASE_URL",

				"spring.ai.zhipuai.embedding.options.model=MODEL_XYZ",
				"spring.ai.zhipuai.embedding.options.encodingFormat=MyEncodingFormat",
				"spring.ai.zhipuai.embedding.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);
				var embeddingProperties = context.getBean(ZhiPuAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void imageOptionsTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.zhipuai.api-key=API_KEY",
						"spring.ai.zhipuai.base-url=TEST_BASE_URL",
						"spring.ai.zhipuai.image.options.model=MODEL_XYZ",
						"spring.ai.zhipuai.image.options.user=userXYZ"
				)
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(ZhiPuAiImageProperties.class);
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

	@Test
	void embeddingActivation() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiEmbeddingModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiEmbeddingModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=zhipuai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=zhipuai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiChatModel.class)).isNotEmpty();
			});

	}

	@Test
	void imageActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",
					"spring.ai.model.image=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiImageModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiImageModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",
					"spring.ai.model.image=zhipuai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ZhiPuAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ZhiPuAiImageModel.class)).isNotEmpty();
			});

	}

}
