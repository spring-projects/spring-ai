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

package org.springframework.ai.tool.observation;

import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Serializes tool definitions as the value of the {@code gen_ai.tool.definitions}
 * attribute: a JSON array following the OpenTelemetry tool definitions schema.
 * <p>
 * The {@code type} and {@code name} of each tool are always included. Following the
 * OpenTelemetry conventions, which do not recommend populating the optional properties by
 * default as they can be large, the {@code description} and {@code parameters} are only
 * included when enabled.
 *
 * @author Ajax-1
 * @since 2.1.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions-genai/blob/main/model/gen-ai/gen-ai-tool-definitions.json">OpenTelemetry
 * tool definitions schema</a>
 */
public final class ToolDefinitionsSerializer {

	private static final JsonMapper JSON_MAPPER = JacksonUtils.getDefaultJsonMapper();

	private final boolean includeDescriptions;

	private final boolean includeParameters;

	/**
	 * Create a serializer.
	 * @param includeDescriptions whether to include the tool descriptions
	 * @param includeParameters whether to include the tool parameter schemas
	 */
	public ToolDefinitionsSerializer(boolean includeDescriptions, boolean includeParameters) {
		this.includeDescriptions = includeDescriptions;
		this.includeParameters = includeParameters;
	}

	/**
	 * Serialize the given tool definitions.
	 * @param toolDefinitions the tool definitions
	 * @return the tool definitions as a JSON array
	 */
	public String serialize(List<ToolDefinition> toolDefinitions) {
		Assert.notNull(toolDefinitions, "toolDefinitions cannot be null");
		ArrayNode array = JSON_MAPPER.createArrayNode();
		toolDefinitions.forEach(toolDefinition -> array.add(toolDefinition(toolDefinition)));
		return JSON_MAPPER.writeValueAsString(array);
	}

	private ObjectNode toolDefinition(ToolDefinition toolDefinition) {
		ObjectNode node = JSON_MAPPER.createObjectNode();
		node.put("type", "function");
		node.put("name", toolDefinition.name());
		if (this.includeDescriptions && StringUtils.hasText(toolDefinition.description())) {
			node.put("description", toolDefinition.description());
		}
		if (this.includeParameters && StringUtils.hasText(toolDefinition.inputSchema())) {
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
