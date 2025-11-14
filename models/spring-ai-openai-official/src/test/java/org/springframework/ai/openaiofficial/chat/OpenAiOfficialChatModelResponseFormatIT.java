/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openaiofficial.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.ResponseFormatJsonSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openaiofficial.OpenAiOfficialChatModel;
import org.springframework.ai.openaiofficial.OpenAiOfficialChatOptions;
import org.springframework.ai.openaiofficial.OpenAiOfficialTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.openaiofficial.OpenAiOfficialChatOptions.DEFAULT_CHAT_MODEL;

/**
 * Integration tests for the response format in {@link OpenAiOfficialChatModel}.
 *
 * @author Julien Dubois
 */
@SpringBootTest(classes = OpenAiOfficialTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiOfficialChatModelResponseFormatIT {

	private static final ObjectMapper MAPPER = new ObjectMapper()
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private OpenAiOfficialChatModel chatModel;

	public static boolean isValidJson(String json) {
		try {
			MAPPER.readTree(json);
		}
		catch (JacksonException e) {
			return false;
		}
		return true;
	}

	@Test
	void jsonObject() {

		Prompt prompt = new Prompt("List 8 planets. Use JSON response",
				OpenAiOfficialChatOptions.builder()
					.responseFormat(OpenAiOfficialChatModel.ResponseFormat.builder()
						.type(OpenAiOfficialChatModel.ResponseFormat.Type.JSON_OBJECT)
						.build())
					.build());

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getText();

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
				OpenAiOfficialChatOptions.builder()
					.model(DEFAULT_CHAT_MODEL)
					.responseFormat(OpenAiOfficialChatModel.ResponseFormat.builder()
							.type(OpenAiOfficialChatModel.ResponseFormat.Type.JSON_SCHEMA)
							.jsonSchema(jsonSchema)
							.build())
					.build());

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getText();

		logger.info("Response content: {}", content);

		assertThat(isValidJson(content)).isTrue();
	}

	@Test
	void jsonSchemaThroughIndividualSetters() throws JsonProcessingException {

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

		ResponseFormatJsonSchema.JsonSchema.Schema responseSchema = MAPPER.readValue(jsonSchema,
				ResponseFormatJsonSchema.JsonSchema.Schema.class);

		Prompt prompt = new Prompt("how can I solve 8x + 7 = -23",
				OpenAiOfficialChatOptions.builder()
					.model(DEFAULT_CHAT_MODEL)
					.responseFormat(OpenAiOfficialChatModel.ResponseFormat.builder().jsonSchema(jsonSchema).build())
					.build());

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getText();

		logger.info("Response content: {}", content);

		assertThat(isValidJson(content)).isTrue();
	}

	@Test
	void jsonSchemaBeanConverter() throws JsonMappingException, JsonProcessingException {

		@JsonPropertyOrder({ "steps", "final_answer" })
		record MathReasoning(@JsonProperty(required = true, value = "steps") Steps steps,
				@JsonProperty(required = true, value = "final_answer") String finalAnswer) {

			record Steps(@JsonProperty(required = true, value = "items") Items[] items) {

				@JsonPropertyOrder({ "output", "explanation" })
				record Items(@JsonProperty(required = true, value = "explanation") String explanation,
						@JsonProperty(required = true, value = "output") String output) {

				}

			}

		}

		var outputConverter = new BeanOutputConverter<>(MathReasoning.class);
		// @formatter:off
		// CHECKSTYLE:OFF
		var expectedJsonSchema = """
				{
				  "$schema" : "https://json-schema.org/draft/2020-12/schema",
				  "type" : "object",
				  "properties" : {
				    "steps" : {
				      "type" : "object",
				      "properties" : {
				        "items" : {
				          "type" : "array",
				          "items" : {
				            "type" : "object",
				            "properties" : {
				              "output" : {
				                "type" : "string"
				              },
				              "explanation" : {
				                "type" : "string"
				              }
				            },
				            "required" : [ "output", "explanation" ],
				            "additionalProperties" : false
				          }
				        }
				      },
				      "required" : [ "items" ],
				      "additionalProperties" : false
				    },
				    "final_answer" : {
				      "type" : "string"
				    }
				  },
				  "required" : [ "steps", "final_answer" ],
				  "additionalProperties" : false
				}""";
		// @formatter:on
		// CHECKSTYLE:ON
		var jsonSchema1 = outputConverter.getJsonSchema();

		assertThat(jsonSchema1).isNotNull();
		assertThat(jsonSchema1).isEqualTo(expectedJsonSchema);

		Prompt prompt = new Prompt("how can I solve 8x + 7 = -23",
				OpenAiOfficialChatOptions.builder()
					.model(DEFAULT_CHAT_MODEL)
					.responseFormat(OpenAiOfficialChatModel.ResponseFormat.builder().jsonSchema(jsonSchema1).build())
					.build());

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getText();

		logger.info("Response content: {}", content);

		assertThat(isValidJson(content)).isTrue();

		// Check if the order is correct as specified in the schema. Steps should come
		// first before final answer.
		assertThat(content.startsWith("{\"steps\":{\"items\":["));

		MathReasoning mathReasoning = outputConverter.convert(content);

		assertThat(mathReasoning).isNotNull();
		logger.info(mathReasoning.toString());
	}

}
