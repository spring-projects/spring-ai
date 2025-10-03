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

package org.springframework.ai.openai.api.tool;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiStrictFunctionSchemaIT {

	OpenAiApi completionApi = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();

	private void invokeOnce(String schemaJson, boolean expectSuccess) {
		var userMsg = new ChatCompletionMessage("Test strict schema", Role.USER);

		var functionTool = new OpenAiApi.FunctionTool(OpenAiApi.FunctionTool.Type.FUNCTION,
				new OpenAiApi.FunctionTool.Function("Test function with strict schema.", "getCurrentWeather",
						ModelOptionsUtils.jsonToMap(schemaJson), true));
		functionTool.getFunction().setStrict(true);

		List<ChatCompletionMessage> messages = new ArrayList<>(List.of(userMsg));
		ChatCompletionRequest req = new ChatCompletionRequest(messages, "gpt-4o", List.of(functionTool),
				ToolChoiceBuilder.AUTO);

		if (expectSuccess) {
			assertThatCode(() -> {
				ResponseEntity<ChatCompletion> ignored = this.completionApi.chatCompletionEntity(req);
			}).doesNotThrowAnyException();
		}
		else {
			assertThatThrownBy(() -> this.completionApi.chatCompletionEntity(req))
				.isInstanceOf(NonTransientAiException.class)
				.hasMessageContaining("Invalid schema for function 'getCurrentWeather'")
				.hasMessageContaining("'additionalProperties' is required to be supplied and to be false");
		}
	}

	@Test
	public void strictSchemaValidation() {
		// Valid schema: additionalProperties=false and required matches properties
		String validSchema = """
				{
				  "type": "object",
				  "properties": {
				    "location": {"type": "string"},
				    "lat": {"type": "number"},
				    "lon": {"type": "number"},
				    "unit": {"type": "string", "enum": ["C", "F"]}
				  },
				  "required": ["location", "lat", "lon", "unit"],
				  "additionalProperties": false
				}
				""";

		// Invalid: missing additionalProperties
		String invalidMissingAdditional = """
				{
				  "type": "object",
				  "properties": {
				    "location": {"type": "string"}
				  },
				  "required": ["location"]
				}
				""";

		// Invalid: additionalProperties true
		String invalidAdditionalTrue = """
				{
				  "type": "object",
				  "properties": {
				    "location": {"type": "string"}
				  },
				  "required": ["location"],
				  "additionalProperties": true
				}
				""";

		invokeOnce(validSchema, true);
		invokeOnce(invalidMissingAdditional, false);
		invokeOnce(invalidAdditionalTrue, false);
	}

}
