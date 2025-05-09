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

package org.springframework.ai.tool.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default conventions to populate observations for tool calling operations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultToolCallingObservationConvention implements ToolCallingObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.tool";

	private final String name;

	public DefaultToolCallingObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultToolCallingObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	@Nullable
	public String getContextualName(ToolCallingObservationContext context) {
		Assert.notNull(context, "context cannot be null");
		String toolName = context.getToolDefinition().name();
		return "%s %s".formatted(SpringAiKind.TOOL_CALL.value(), toolName);
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ToolCallingObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), springAiKind(context),
				toolDefinitionName(context));
	}

	protected KeyValue aiOperationType(ToolCallingObservationContext context) {
		return KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(ToolCallingObservationContext context) {
		return KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue springAiKind(ToolCallingObservationContext context) {
		return KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND,
				SpringAiKind.TOOL_CALL.value());
	}

	protected KeyValue toolDefinitionName(ToolCallingObservationContext context) {
		String toolName = context.getToolDefinition().name();
		return KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.TOOL_DEFINITION_NAME, toolName);
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ToolCallingObservationContext context) {
		var keyValues = KeyValues.empty();
		keyValues = toolDefinitionDescription(keyValues, context);
		keyValues = toolDefinitionSchema(keyValues, context);
		return keyValues;
	}

	protected KeyValues toolDefinitionDescription(KeyValues keyValues, ToolCallingObservationContext context) {
		String toolDescription = context.getToolDefinition().description();
		return keyValues.and(
				ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_DESCRIPTION.asString(),
				toolDescription);
	}

	protected KeyValues toolDefinitionSchema(KeyValues keyValues, ToolCallingObservationContext context) {
		String toolSchema = context.getToolDefinition().inputSchema();
		return keyValues.and(
				ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_SCHEMA.asString(),
				toolSchema);
	}

}
