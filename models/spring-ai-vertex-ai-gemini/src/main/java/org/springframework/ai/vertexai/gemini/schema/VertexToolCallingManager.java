/*
* Copyright 2025 - 2025 the original author or authors.
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
package org.springframework.ai.vertexai.gemini.schema;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class VertexToolCallingManager implements ToolCallingManager {

	private final ToolCallingManager delegateToolCallingManager;

	public VertexToolCallingManager(ToolCallingManager delegateToolCallingManager) {
		this.delegateToolCallingManager = delegateToolCallingManager;
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {

		List<ToolDefinition> toolDefinitions = delegateToolCallingManager.resolveToolDefinitions(chatOptions);

		return toolDefinitions.stream().map(td -> {
			ObjectNode jsonSchema = JsonSchemaConverter.fromJson(td.inputSchema());
			ObjectNode openApiSchema = JsonSchemaConverter.convertToOpenApiSchema(jsonSchema);
			JsonSchemaGenerator.convertTypeValuesToUpperCase(openApiSchema);

			return ToolDefinition.builder()
				.name(td.name())
				.description(td.description())
				.inputSchema(openApiSchema.toPrettyString())
				.build();
		}).toList();
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		return this.delegateToolCallingManager.executeToolCalls(prompt, chatResponse);
	}

}
