/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

import org.springframework.ai.openai.OpenAiAssistantManager;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.AssistantResponse;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.CreateAssistantRequest;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.ModifyAssistantRequest;
import org.springframework.ai.openai.api.assistants.ResponseFormat;
import org.springframework.ai.openai.api.assistants.tools.FunctionTool;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test for OpenAiAssistantManager lifecycle: create, retrieve, modify, list,
 * and delete using high-level abstraction over the OpenAiAssistantApi.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAssistantManagerIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiAssistantManagerIT.class);

	private final OpenAiAssistantApi openAiAssistantApi = new OpenAiAssistantApi(System.getenv("OPENAI_API_KEY"));

	private final OpenAiAssistantManager assistantManager = new OpenAiAssistantManager(openAiAssistantApi);

	@Test
	void testCreateAndRetrieveAssistant() {
		// Create an assistant
		CreateAssistantRequest createAssistantRequest = CreateAssistantRequest.builder()
			.withModel("gpt-4o-mini-2024-07-18")
			.withName("Assistant Manager Test")
			.withDescription("Created by Assistant Manager Test")
			.withInstructions("Follow the test instructions.")
			.withTools(List.of(new FunctionTool(new FunctionTool.Function("test_tool", "description", null, false))))
			.withMetadata(Map.of("key", "value"))
			.withTemperature(0.7)
			.withTopP(0.9)
			.withResponseFormat(ResponseFormat.auto())
			.build();

		AssistantResponse createdAssistant = assistantManager.createAssistant(createAssistantRequest);

		assertThat(createdAssistant.id()).isNotNull();
		assertThat(createdAssistant.name()).isEqualTo("Assistant Manager Test");
		assertThat(createdAssistant.model()).isEqualTo("gpt-4o-mini-2024-07-18");
		logger.info("Created Assistant: {}", createdAssistant);

		// Retrieve the assistant
		AssistantResponse retrievedAssistant = assistantManager.retrieveAssistant(createdAssistant.id());

		assertThat(retrievedAssistant).isNotNull();
		assertThat(retrievedAssistant.id()).isEqualTo(createdAssistant.id());
		assertThat(retrievedAssistant.name()).isEqualTo("Assistant Manager Test");
		logger.info("Retrieved Assistant: {}", retrievedAssistant);
	}

	@Test
	void testModifyAssistant() {
		// Create an assistant
		var createRequest = CreateAssistantRequest.builder()
			.withModel("gpt-4o-mini-2024-07-18")
			.withName("Assistant Modify Test")
			.withDescription("Initial description")
			.withInstructions("Initial instructions.")
			.withTools(List.of(new FunctionTool(new FunctionTool.Function("test_tool", "description", null, false))))
			.withMetadata(Map.of("key", "value"))
			.withTemperature(0.7)
			.withTopP(0.9)
			.withResponseFormat(ResponseFormat.auto())
			.build();

		AssistantResponse createdAssistant = assistantManager.createAssistant(createRequest);

		// Modify the assistant
		var modifyRequest = new ModifyAssistantRequest("gpt-4o-mini-2024-07-18", "Assistant Modify Test Updated",
				"Updated description", "Updated instructions.",
				List.of(new FunctionTool(
						new FunctionTool.Function("updated_tool", "updated description", null, false))),
				null, Map.of("key", "updated_value"), 0.6, 0.8, ResponseFormat.auto());

		AssistantResponse modifiedAssistant = assistantManager.modifyAssistant(createdAssistant.id(), modifyRequest);

		assertThat(modifiedAssistant).isNotNull();
		assertThat(modifiedAssistant.name()).isEqualTo("Assistant Modify Test Updated");
		assertThat(modifiedAssistant.description()).isEqualTo("Updated description");
		logger.info("Modified Assistant: {}", modifiedAssistant);
	}

	@Test
	void testCreateRetrieveAndListAssistants() {
		// Step 1: Create an assistant
		var createRequest = CreateAssistantRequest.builder()
			.withModel("gpt-4o-mini-2024-07-18")
			.withName("Assistant List Test")
			.withDescription("Created for listing test")
			.withInstructions("List and validate this assistant.")
			.withTools(List.of(new FunctionTool(new FunctionTool.Function("test_tool", "description", null, false))))
			.withMetadata(Map.of("key", "value"))
			.withTemperature(0.7)
			.withTopP(0.9)
			.withResponseFormat(ResponseFormat.auto())
			.build();

		AssistantResponse createdAssistant = assistantManager.createAssistant(createRequest);

		assertThat(createdAssistant).isNotNull();
		String createdAssistantId = createdAssistant.id();
		assertThat(createdAssistantId).isNotEmpty();
		logger.info("Created Assistant for listing test: {}", createdAssistant);

		// Step 2: List assistants and validate the created assistant is in the list
		OpenAiAssistantApi.ListAssistantsResponse listResponse = assistantManager.listAssistants(10, "desc", null,
				null);

		assertThat(listResponse.data()).isNotNull();
		assertThat(listResponse.data()).isNotEmpty();
		boolean assistantFound = listResponse.data()
			.stream()
			.anyMatch(assistant -> assistant.id().equals(createdAssistantId));
		assertThat(assistantFound).isTrue();
		logger.info("Validated Assistant in List: {}", createdAssistantId);
	}

	@Test
	void testDeleteAssistant() {
		// Create an assistant
		var createRequest = CreateAssistantRequest.builder()
			.withModel("gpt-4o-mini-2024-07-18")
			.withName("Assistant Delete Test")
			.withDescription("Created for deletion")
			.withInstructions("Delete me.")
			.withTools(List.of(new FunctionTool(new FunctionTool.Function("test_tool", "description", null, false))))
			.withMetadata(Map.of("key", "value"))
			.withTemperature(0.7)
			.withTopP(0.9)
			.withResponseFormat(ResponseFormat.auto())
			.build();

		AssistantResponse createdAssistant = assistantManager.createAssistant(createRequest);

		// Delete the assistant
		boolean deleted = assistantManager.deleteAssistant(createdAssistant.id());

		assertThat(deleted).isTrue();
		logger.info("Deleted Assistant ID: {}", createdAssistant.id());
	}

}
