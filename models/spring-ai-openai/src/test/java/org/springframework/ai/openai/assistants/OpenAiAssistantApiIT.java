/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.openai.assistants;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.*;
import org.springframework.ai.openai.api.assistants.ResponseFormat;
import org.springframework.ai.openai.api.assistants.tools.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test for OpenAiAssistantApi lifecycle: create, retrieve, modify, list, and
 * delete.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAssistantApiIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiAssistantApiIT.class);

	private final OpenAiAssistantApi openAiAssistantApi = new OpenAiAssistantApi(System.getenv("OPENAI_API_KEY"));

	@Test
	void testCreateAssistantWithAutoResponseFormat() {
		var createRequest = new CreateAssistantRequest("gpt-4o-mini-2024-07-18", "Assistant Auto",
				"Assistant with auto response format.", "You are an assistant with auto response format.",
				List.of(new FileSearchTool(null)), new ToolResources(null, null), Map.of("environment", "test"), 0.7,
				0.9, ResponseFormat.auto());

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		assertThat(createResponse.responseFormat().getType()).isEqualTo(ResponseFormatType.AUTO);
		logger.info("Assistant with auto response format created: {}", createResponse);
	}

	@Test
	void testCreateAssistantWithoutResponseFormat() {
		var createRequest = new CreateAssistantRequest("gpt-4o-mini-2024-07-18", "Assistant Auto",
				"Assistant with auto response format.", "You are an assistant with auto response format.",
				List.of(new FileSearchTool(null)), new ToolResources(null, null), Map.of("environment", "test"), 0.7,
				0.9, null);

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		assertThat(createResponse.responseFormat().getType()).isEqualTo(ResponseFormatType.AUTO);
		logger.info("Assistant with auto response format created: {}", createResponse);
	}

	@Test
	void testCreateAssistantWithTextResponseFormat() {
		var createRequest = new CreateAssistantRequest("gpt-4o-mini-2024-07-18", "Assistant Text",
				"Assistant with text response format.", "You are an assistant with text response format.",
				List.of(new FileSearchTool(null)), new ToolResources(null, null), Map.of("environment", "test"), 0.7,
				0.9, ResponseFormat.text());

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		assertThat(createResponse.responseFormat().getType()).isEqualTo(ResponseFormatType.TEXT);
		logger.info("Assistant with text response format created: {}", createResponse);
	}

	@Test
	void testCreateAssistantWithJsonObjectResponseFormat() {
		var createRequest = new CreateAssistantRequest("gpt-4o-mini-2024-07-18", "Assistant JSON Object",
				"Assistant with JSON Object response format.", "You are an assistant with JSON Object response format.",
				List.of(new FunctionTool(new FunctionTool.Function("process_data", "Process data", null, false))),
				new ToolResources(null, null), Map.of("environment", "test"), 0.7, 0.9, ResponseFormat.jsonObject());

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		assertThat(createResponse.responseFormat().getType()).isEqualTo(ResponseFormatType.JSON_OBJECT);
		logger.info("Assistant with JSON Object response format created: {}", createResponse);
	}

	@Test
	void testCreateAssistantWithJsonSchemaResponseFormat() {
		var schema = Map.of("type", "object", "properties", Map.of("key", Map.of("type", "string")), "required",
				List.of("key"), "additionalProperties", false);

		var jsonSchema = new JsonSchema("Schema for response validation.", "TestSchema", schema, true);

		var createRequest = new CreateAssistantRequest("gpt-4o-mini-2024-07-18", "Assistant JSON Schema",
				"Assistant with JSON Schema response format.", "You are an assistant with JSON Schema response format.",
				List.of(new FunctionTool(new FunctionTool.Function("process_schema", "Process schema", null, false))),
				new ToolResources(null, null), Map.of("environment", "test"), 0.7, 0.9,
				ResponseFormat.jsonSchema(jsonSchema));

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		assertThat(createResponse.responseFormat().getType()).isEqualTo(ResponseFormatType.JSON_SCHEMA);
		logger.info("Assistant with JSON Schema response format created: {}", createResponse);
	}

	@Test
	void testCreateAssistantUsingBuilder() {
		var createRequest = CreateAssistantRequest.builder()
			.withModel("gpt-4o-mini-2024-07-18")
			.withName("Assistant Builder")
			.withDescription("Assistant created using the Builder pattern.")
			.withInstructions("You are an assistant created using the Builder pattern.")
			.withTools(
					List.of(new FunctionTool(new FunctionTool.Function("builder_tool", "Processes data", null, false))))
			.withToolResources(new ToolResources(null, null))
			.withMetadata(Map.of("environment", "builder_test"))
			.withTemperature(0.7)
			.withTopP(0.9)
			.withResponseFormat(ResponseFormat.jsonObject())
			.build();

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		assertThat(createResponse.name()).isEqualTo("Assistant Builder");
		assertThat(createResponse.model()).isEqualTo("gpt-4o-mini-2024-07-18");
		assertThat(createResponse.responseFormat().getType()).isEqualTo(ResponseFormatType.JSON_OBJECT);
		logger.info("Assistant created using Builder pattern: {}", createResponse);
	}

	@Test
	void assistantLifecycleTest() {
		// Step 1: Create an assistant
		var createRequest = new CreateAssistantRequest("gpt-4o-mini-2024-07-18", "Test Assistant",
				"This is a test assistant", "You are a helpful assistant.", List.of(new FileSearchTool(null)),
				new ToolResources(null, null), Map.of("key", "value"), 0.7, 0.9, ResponseFormat.auto());

		var createResponse = openAiAssistantApi.createAssistant(createRequest).getBody();

		assertThat(createResponse).isNotNull();
		String assistantId = createResponse.id();
		assertThat(assistantId).isNotEmpty();
		assertThat(createResponse.name()).isEqualTo("Test Assistant");
		assertThat(createResponse.model()).isEqualTo("gpt-4o-mini-2024-07-18");
		System.out.println("Created Assistant ID: " + assistantId);

		// Step 2: Retrieve the created assistant
		var retrieveResponse = openAiAssistantApi.retrieveAssistant(assistantId).getBody();

		assertThat(retrieveResponse).isNotNull();
		assertThat(retrieveResponse.id()).isEqualTo(assistantId);
		assertThat(retrieveResponse.name()).isEqualTo("Test Assistant");
		logger.info("Retrieved Assistant: " + retrieveResponse);

		// Step 3: Modify the assistant
		var modifyRequest = new ModifyAssistantRequest("gpt-4o-mini-2024-07-18", "Modified Assistant",
				"This is a modified assistant", "You are a very helpful assistant.",
				List.of(new FunctionTool(new FunctionTool.Function("name", "description", null, false))),
				new ToolResources(null, null), Map.of("version", "v2"), 0.6, 0.8, ResponseFormat.auto());

		var modifyResponse = openAiAssistantApi.modifyAssistant(assistantId, modifyRequest).getBody();

		assertThat(modifyResponse).isNotNull();
		assertThat(modifyResponse.id()).isEqualTo(assistantId);
		assertThat(modifyResponse.name()).isEqualTo("Modified Assistant");
		logger.info("Modified Assistant: " + modifyResponse);

		// Step 4: List assistants
		var listResponse = openAiAssistantApi.listAssistants(10, "desc", null, null).getBody();

		assertThat(listResponse).isNotNull();
		assertThat(listResponse.data()).isNotEmpty();
		logger.info("List of Assistants: " + listResponse.data());

		// Step 5: Delete the assistant
		var deleteResponse = openAiAssistantApi.deleteAssistant(assistantId).getBody();

		assertThat(deleteResponse).isNotNull();
		assertThat(deleteResponse.deleted()).isTrue();
		logger.info("Deleted Assistant ID: " + assistantId);
	}

}
