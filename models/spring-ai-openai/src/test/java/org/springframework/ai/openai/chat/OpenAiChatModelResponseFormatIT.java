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
package org.springframework.ai.openai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiApi.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = OpenAiChatModelResponseFormatIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatModelResponseFormatIT {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private OpenAiChatModel openAiChatModel;

	@Test
	void jsonObject() throws JsonMappingException, JsonProcessingException {

		// 400 - ResponseError[error=Error[message='json' is not one of ['json_object',
		// 'text'] -
		// 'response_format.type', type=invalid_request_error, param=null, code=null]]

		// 400 - ResponseError[error=Error[message='messages' must contain the word 'json'
		// in some form, to use
		// 'response_format' of type 'json_object'., type=invalid_request_error,
		// param=messages, code=null]]

		Prompt prompt = new Prompt("List 8 planets. Use JSON response",
				OpenAiChatOptions.builder()
					.withResponseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT))
					.build());

		ChatResponse response = this.openAiChatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getContent();

		logger.info("Response content: {}", content);

		assertThat(isValidJson(content)).isTrue();
	}

	@Test
	void jsonSchema() throws JsonMappingException, JsonProcessingException {

		var jsonSchema = """
				{
					"type": "object",
					"properties": {
						"steps": {
							"type": "array",
							"items": {
								"type": "object",
								"properties": {
									"explanation": { "type": "string" },
									"output": { "type": "string" }
								},
								"required": ["explanation", "output"],
								"additionalProperties": false
							}
						},
						"final_answer": { "type": "string" }
					},
					"required": ["steps", "final_answer"],
					"additionalProperties": false
				}
				""";

		Prompt prompt = new Prompt("how can I solve 8x + 7 = -23",
				OpenAiChatOptions.builder()
					.withModel(ChatModel.GPT_4_O_MINI)
					.withResponseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
					.build());

		ChatResponse response = this.openAiChatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getContent();

		logger.info("Response content: {}", content);

		assertThat(isValidJson(content)).isTrue();
	}

	@Test
	void jsonSchemaBeanConverter() throws JsonMappingException, JsonProcessingException {

		record MathReasoning(@JsonProperty(required = true, value = "steps") Steps steps,
				@JsonProperty(required = true, value = "final_answer") String finalAnswer) {

			record Steps(@JsonProperty(required = true, value = "items") Items[] items) {

				record Items(@JsonProperty(required = true, value = "explanation") String explanation,
						@JsonProperty(required = true, value = "output") String output) {
				}
			}
		}

		var outputConverter = new BeanOutputConverter<>(MathReasoning.class);

		var jsonSchema1 = outputConverter.getJsonSchema();

		System.out.println(jsonSchema1);

		Prompt prompt = new Prompt("how can I solve 8x + 7 = -23",
				OpenAiChatOptions.builder()
					.withModel(ChatModel.GPT_4_O_MINI)
					.withResponseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema1))
					.build());

		ChatResponse response = this.openAiChatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getContent();

		logger.info("Response content: {}", content);

		MathReasoning mathReasoning = outputConverter.convert(content);

		System.out.println(mathReasoning);

		assertThat(isValidJson(content)).isTrue();
	}

	private static ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	public static boolean isValidJson(String json) {
		try {
			MAPPER.readTree(json);
		}
		catch (JacksonException e) {
			return false;
		}
		return true;
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatModel(openAiApi);
		}

	}

}
