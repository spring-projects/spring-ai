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

package org.springframework.ai.mcp.server.common.autoconfigure.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.util.Assert;

/**
 * Default conventions to populate observations for MCP server tool call operations.
 *
 * @author Michal Grandys
 * @since 2.0.0
 */
public class DefaultMcpServerToolObservationConvention implements McpServerToolObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.mcp.server.tool";

	private final String name;

	public DefaultMcpServerToolObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultMcpServerToolObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getContextualName(McpServerToolObservationContext context) {
		Assert.notNull(context, "context cannot be null");
		String toolName = context.getToolDefinition().name();
		return "%s %s".formatted(AiOperationType.EXECUTE_TOOL.value(), toolName);
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(McpServerToolObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), springAiKind(context), toolType(context),
				toolDefinitionName(context), mcpServerProtocol(context), mcpServerType(context));
	}

	protected KeyValue aiOperationType(McpServerToolObservationContext context) {
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(McpServerToolObservationContext context) {
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue springAiKind(McpServerToolObservationContext context) {
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND,
				SpringAiKind.MCP_SERVER_TOOL_CALL.value());
	}

	protected KeyValue toolType(McpServerToolObservationContext context) {
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.TOOL_TYPE,
				context.getToolType());
	}

	protected KeyValue toolDefinitionName(McpServerToolObservationContext context) {
		String toolName = context.getToolDefinition().name();
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.TOOL_DEFINITION_NAME, toolName);
	}

	protected KeyValue mcpServerProtocol(McpServerToolObservationContext context) {
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.MCP_SERVER_PROTOCOL,
				context.getMcpServerProtocol());
	}

	protected KeyValue mcpServerType(McpServerToolObservationContext context) {
		return KeyValue.of(McpServerToolObservationDocumentation.LowCardinalityKeyNames.MCP_SERVER_TYPE,
				context.getMcpServerType());
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(McpServerToolObservationContext context) {
		var keyValues = KeyValues.empty();
		keyValues = toolDefinitionDescription(keyValues, context);
		keyValues = toolDefinitionSchema(keyValues, context);
		return keyValues;
	}

	protected KeyValues toolDefinitionDescription(KeyValues keyValues, McpServerToolObservationContext context) {
		String toolDescription = context.getToolDefinition().description();
		return keyValues.and(
				McpServerToolObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_DESCRIPTION.asString(),
				toolDescription);
	}

	protected KeyValues toolDefinitionSchema(KeyValues keyValues, McpServerToolObservationContext context) {
		String toolSchema = context.getToolDefinition().inputSchema();
		return keyValues.and(
				McpServerToolObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_SCHEMA.asString(),
				toolSchema);
	}

}
