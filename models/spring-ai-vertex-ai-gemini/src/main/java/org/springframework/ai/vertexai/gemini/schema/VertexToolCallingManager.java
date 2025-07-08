/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vertexai.gemini.schema;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.util.Assert;

/**
 * Implementation of {@link ToolCallingManager} specifically designed for Vertex AI
 * Gemini. This manager adapts tool definitions to be compatible with Vertex AI's OpenAPI
 * schema format by converting JSON schemas and ensuring proper type value upper-casing.
 *
 * <p>
 * It delegates the actual tool execution to another {@link ToolCallingManager} while
 * handling the necessary schema conversions for Vertex AI compatibility.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class VertexToolCallingManager implements ToolCallingManager {

	/**
	 * The underlying tool calling manager that handles actual tool execution.
	 */
	private final ToolCallingManager delegateToolCallingManager;

	/**
	 * Creates a new instance of VertexToolCallingManager.
	 * @param delegateToolCallingManager the underlying tool calling manager that handles
	 * actual tool execution
	 */
	public VertexToolCallingManager(ToolCallingManager delegateToolCallingManager) {
		Assert.notNull(delegateToolCallingManager, "Delegate tool calling manager must not be null");
		this.delegateToolCallingManager = delegateToolCallingManager;
	}

	/**
	 * Resolves tool definitions and converts their input schemas to be compatible with
	 * Vertex AI's OpenAPI format. This includes converting JSON schemas to OpenAPI format
	 * and ensuring proper type value casing.
	 * @param chatOptions the options containing tool preferences and configurations
	 * @return a list of tool definitions with Vertex AI compatible schemas
	 */
	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {

		List<ToolDefinition> toolDefinitions = this.delegateToolCallingManager.resolveToolDefinitions(chatOptions);

		return toolDefinitions.stream().map(td -> {
			ObjectNode jsonSchema = JsonSchemaConverter.fromJson(td.inputSchema());
			ObjectNode openApiSchema = JsonSchemaConverter.convertToOpenApiSchema(jsonSchema);
			JsonSchemaGenerator.convertTypeValuesToUpperCase(openApiSchema);

			return DefaultToolDefinition.builder()
				.name(td.name())
				.description(td.description())
				.inputSchema(openApiSchema.toPrettyString())
				.build();
		}).toList();
	}

	/**
	 * Executes tool calls by delegating to the underlying tool calling manager.
	 * @param prompt the original prompt that triggered the tool calls
	 * @param chatResponse the chat response containing the tool calls to execute
	 * @return the result of executing the tool calls
	 */
	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		return this.delegateToolCallingManager.executeToolCalls(prompt, chatResponse);
	}

}
