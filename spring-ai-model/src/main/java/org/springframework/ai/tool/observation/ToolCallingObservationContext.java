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

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Context used to store data for tool calling observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class ToolCallingObservationContext extends Observation.Context {

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.EXECUTE_TOOL.value(),
			AiProvider.SPRING_AI.value());

	private final ToolDefinition toolDefinition;

	private final ToolMetadata toolMetadata;

	private final String toolType;

	private final String toolCallId;

	private final String toolCallArguments;

	private @Nullable String toolCallResult;

	private ToolCallingObservationContext(ToolDefinition toolDefinition, ToolMetadata toolMetadata,
			@Nullable String toolType, @Nullable String toolCallId, @Nullable String toolCallArguments,
			@Nullable String toolCallResult) {
		Assert.notNull(toolDefinition, "toolDefinition cannot be null");
		Assert.notNull(toolMetadata, "toolMetadata cannot be null");

		this.toolDefinition = toolDefinition;
		this.toolMetadata = toolMetadata;
		this.toolType = StringUtils.hasText(toolType) ? toolType : "function";
		this.toolCallId = StringUtils.hasText(toolCallId) ? toolCallId : "";
		this.toolCallArguments = StringUtils.hasText(toolCallArguments) ? toolCallArguments : "{}";
		this.toolCallResult = toolCallResult;
	}

	public AiOperationMetadata getOperationMetadata() {
		return this.operationMetadata;
	}

	public ToolDefinition getToolDefinition() {
		return this.toolDefinition;
	}

	public ToolMetadata getToolMetadata() {
		return this.toolMetadata;
	}

	public String getToolCallId() {
		return this.toolCallId;
	}

	public String getToolType() {
		return this.toolType;
	}

	public String getToolCallArguments() {
		return this.toolCallArguments;
	}

	public @Nullable String getToolCallResult() {
		return this.toolCallResult;
	}

	public void setToolCallResult(@Nullable String toolCallResult) {
		this.toolCallResult = toolCallResult;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable ToolDefinition toolDefinition;

		private ToolMetadata toolMetadata = ToolMetadata.builder().build();

		private @Nullable String toolType;

		private @Nullable String toolCallId;

		private @Nullable String toolCallArguments;

		private @Nullable String toolCallResult;

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

		public Builder toolType(String toolCallType) {
			this.toolType = toolCallType;
			return this;
		}

		public Builder toolCallId(String toolCallId) {
			this.toolCallId = toolCallId;
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
			Assert.notNull(this.toolDefinition, "toolDefinition cannot be null");
			return new ToolCallingObservationContext(this.toolDefinition, this.toolMetadata, this.toolType,
					this.toolCallId, this.toolCallArguments, this.toolCallResult);
		}

	}

}
