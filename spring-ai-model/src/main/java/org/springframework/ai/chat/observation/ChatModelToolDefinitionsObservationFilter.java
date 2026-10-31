/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.observation;

import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link ObservationFilter} to include the definitions of the tools provided to the
 * model in the chat model observation, as the {@code gen_ai.tool.definitions} attribute.
 * The value follows the OpenTelemetry tool definitions schema: a JSON array of objects
 * with the tool {@code type}, {@code name}, {@code description} and {@code parameters}.
 *
 * @author Ajax-1
 * @since 2.1.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions-genai/blob/main/model/gen-ai/gen-ai-tool-definitions.json">OpenTelemetry
 * tool definitions schema</a>
 */
public class ChatModelToolDefinitionsObservationFilter implements ObservationFilter {

	private static final JsonMapper JSON_MAPPER = JacksonUtils.getDefaultJsonMapper();

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
			return context;
		}
		if (!(chatModelObservationContext.getRequest().getOptions() instanceof ToolCallingChatOptions options)) {
			return chatModelObservationContext;
		}
		List<ToolCallback> toolCallbacks = options.getToolCallbacks();
		if (CollectionUtils.isEmpty(toolCallbacks)) {
			return chatModelObservationContext;
		}

		ArrayNode toolDefinitions = JSON_MAPPER.createArrayNode();
		toolCallbacks.forEach(toolCallback -> toolDefinitions.add(toolDefinition(toolCallback.getToolDefinition())));

		chatModelObservationContext.addHighCardinalityKeyValue(
				ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_DEFINITIONS
					.withValue(JSON_MAPPER.writeValueAsString(toolDefinitions)));

		return chatModelObservationContext;
	}

	private static ObjectNode toolDefinition(ToolDefinition toolDefinition) {
		ObjectNode node = JSON_MAPPER.createObjectNode();
		node.put("type", "function");
		node.put("name", toolDefinition.name());
		if (StringUtils.hasText(toolDefinition.description())) {
			node.put("description", toolDefinition.description());
		}
		if (StringUtils.hasText(toolDefinition.inputSchema())) {
			try {
				node.set("parameters", JSON_MAPPER.readTree(toolDefinition.inputSchema()));
			}
			catch (JacksonException ex) {
				// The schema is not valid JSON: the tool is still described, without its
				// parameters.
			}
		}
		return node;
	}

}
