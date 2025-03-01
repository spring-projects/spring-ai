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

package org.springframework.ai.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

/**
 * Represents a tool whose execution can be triggered by an AI model.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolCallback extends FunctionCallback {

	/**
	 * Definition used by the AI model to determine when and how to call the tool.
	 */
	ToolDefinition getToolDefinition();

	/**
	 * Metadata providing additional information on how to handle the tool.
	 */
	default ToolMetadata getToolMetadata() {
		return ToolMetadata.builder().build();
	}

	/**
	 * Execute tool with the given input and return the result to send back to the AI
	 * model.
	 */
	String call(String toolInput);

	/**
	 * Execute tool with the given input and context, and return the result to send back
	 * to the AI model.
	 */
	default String call(String toolInput, @Nullable ToolContext tooContext) {
		if (tooContext != null && !tooContext.getContext().isEmpty()) {
			throw new UnsupportedOperationException("Tool context is not supported!");
		}
		return call(toolInput);
	}

	@Override
	@Deprecated // Call getToolDefinition().name() instead
	default String getName() {
		return getToolDefinition().name();
	}

	@Override
	@Deprecated // Call getToolDefinition().description() instead
	default String getDescription() {
		return getToolDefinition().description();
	}

	@Override
	@Deprecated // Call getToolDefinition().inputTypeSchema() instead
	default String getInputTypeSchema() {
		return getToolDefinition().inputSchema();
	}

}
