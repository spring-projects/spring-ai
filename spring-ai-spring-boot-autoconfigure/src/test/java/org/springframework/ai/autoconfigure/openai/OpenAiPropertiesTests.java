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

package org.springframework.ai.autoconfigure.openai;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.api.OpenAiApi.FunctionTool.Type;
import org.springframework.ai.openai.api.OpenAiApi.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiApi.ToolChoice;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OpenAiPropertiesTests {

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=API_KEY",

				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.frequencyPenalty=-1.5",
				"spring.ai.openai.chat.options.logitBias.myTokenId=-5",
				"spring.ai.openai.chat.options.maxTokens=123",
				"spring.ai.openai.chat.options.n=10",
				"spring.ai.openai.chat.options.presencePenalty=0",
				"spring.ai.openai.chat.options.responseFormat.type=json",
				"spring.ai.openai.chat.options.seed=66",
				"spring.ai.openai.chat.options.stop=boza,koza",
				"spring.ai.openai.chat.options.stream=true",
				"spring.ai.openai.chat.options.temperature=0.55",
				"spring.ai.openai.chat.options.topP=0.56",
				"spring.ai.openai.chat.options.toolChoice.functionName=toolChoiceFunctionName",

				"spring.ai.openai.chat.options.tools[0].function.name=myFunction1",
				"spring.ai.openai.chat.options.tools[0].function.description=function description",
				"spring.ai.openai.chat.options.tools[0].function.jsonSchema=" + """
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
					"spring.ai.openai.chat.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getModel()).isEqualTo("text-embedding-ada-002");

				assertThat(chatProperties.getOptions().model()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().frequencyPenalty()).isEqualTo(-1.5f);
				assertThat(chatProperties.getOptions().logitBias()).containsKey("myTokenId");
				assertThat(chatProperties.getOptions().logitBias().get("myTokenId")).isEqualTo(-5);
				assertThat(chatProperties.getOptions().maxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().n()).isEqualTo(10);
				assertThat(chatProperties.getOptions().presencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().responseFormat()).isEqualTo(new ResponseFormat("json"));
				assertThat(chatProperties.getOptions().seed()).isEqualTo(66);
				assertThat(chatProperties.getOptions().stop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().stream()).isTrue();
				assertThat(chatProperties.getOptions().temperature()).isEqualTo(0.55f);
				assertThat(chatProperties.getOptions().topP()).isEqualTo(0.56f);

				assertThat(chatProperties.getOptions().toolChoice())
					.isEqualTo(new ToolChoice("function", Map.of("name", "toolChoiceFunctionName")));
				assertThat(chatProperties.getOptions().user()).isEqualTo("userXYZ");

				assertThat(chatProperties.getOptions().tools()).hasSize(1);
				var tool = chatProperties.getOptions().tools().get(0);
				assertThat(tool.type()).isEqualTo(Type.function);
				var function = tool.function();
				assertThat(function.name()).isEqualTo("myFunction1");
				assertThat(function.description()).isEqualTo("function description");
				assertThat(function.parameters()).isNotEmpty();

			});
	}

}
