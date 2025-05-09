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

import io.micrometer.observation.Observation;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Context used to store data for tool calling observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ToolCallingObservationContext extends Observation.Context {

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.FRAMEWORK.value(),
			AiProvider.SPRING_AI.value());

	private final ToolDefinition toolDefinition;

	private final ToolMetadata toolMetadata;

	private final String toolCallArguments;

	@Nullable
	private String toolCallResult;

	private ToolCallingObservationContext(ToolDefinition toolDefinition, ToolMetadata toolMetadata,
			String toolCallArguments, @Nullable String toolCallResult) {
		Assert.notNull(toolDefinition, "toolDefinition cannot be null");
		Assert.notNull(toolMetadata, "toolMetadata cannot be null");
		Assert.hasText(toolCallArguments, "toolCallArguments cannot be null or empty");

		this.toolDefinition = toolDefinition;
		this.toolMetadata = toolMetadata;
		this.toolCallArguments = toolCallArguments;
		this.toolCallResult = toolCallResult;
	}

	public AiOperationMetadata getOperationMetadata() {
		return operationMetadata;
	}

	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	public ToolMetadata getToolMetadata() {
		return toolMetadata;
	}

	public String getToolCallArguments() {
		return toolCallArguments;
	}

	@Nullable
	public String getToolCallResult() {
		return toolCallResult;
	}

	public void setToolCallResult(@Nullable String toolCallResult) {
		this.toolCallResult = toolCallResult;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ToolDefinition toolDefinition;

		private ToolMetadata toolMetadata = ToolMetadata.builder().build();

		private String toolCallArguments;

		@Nullable
		private String toolCallResult;

		private Builder() {
		}

		public Builder toolDefinition(ToolDefinition toolDefinition) {
			this.toolDefinition = toolDefinition;
			return this;
		}

		public Builder toolMetadata(ToolMetadata toolMetadata) {
			this.toolMetadata = toolMetadata;
			return this;
		}

		public Builder toolCallArguments(String toolCallArguments) {
			this.toolCallArguments = toolCallArguments;
			return this;
		}

		public Builder toolCallResult(@Nullable String toolCallResult) {
			this.toolCallResult = toolCallResult;
			return this;
		}

		public ToolCallingObservationContext build() {
			return new ToolCallingObservationContext(toolDefinition, toolMetadata, toolCallArguments, toolCallResult);
		}

	}

}
