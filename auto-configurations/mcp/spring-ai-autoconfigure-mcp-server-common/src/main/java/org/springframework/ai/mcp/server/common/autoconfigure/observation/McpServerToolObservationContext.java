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

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Context used to store data for MCP server tool call observations.
 *
 * @author Michal Grandys
 * @since 2.0.0
 */
public final class McpServerToolObservationContext extends Observation.Context {

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.EXECUTE_TOOL.value(),
			AiProvider.SPRING_AI.value());

	private final ToolDefinition toolDefinition;

	private final String toolType;

	private final String toolCallArguments;

	private final String mcpServerProtocol;

	private final String mcpServerType;

	private @Nullable String toolCallResult;

	private McpServerToolObservationContext(ToolDefinition toolDefinition, @Nullable String toolType,
			@Nullable String toolCallArguments, @Nullable String toolCallResult, String mcpServerProtocol,
			String mcpServerType) {
		Assert.notNull(toolDefinition, "toolDefinition cannot be null");
		Assert.hasText(mcpServerProtocol, "mcpServerProtocol cannot be empty");
		Assert.hasText(mcpServerType, "mcpServerType cannot be empty");

		this.toolDefinition = toolDefinition;
		this.toolType = StringUtils.hasText(toolType) ? toolType : "function";
		this.toolCallArguments = StringUtils.hasText(toolCallArguments) ? toolCallArguments : "{}";
		this.toolCallResult = toolCallResult;
		this.mcpServerProtocol = mcpServerProtocol;
		this.mcpServerType = mcpServerType;
	}

	public AiOperationMetadata getOperationMetadata() {
		return this.operationMetadata;
	}

	public ToolDefinition getToolDefinition() {
		return this.toolDefinition;
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

	public String getMcpServerProtocol() {
		return this.mcpServerProtocol;
	}

	public String getMcpServerType() {
		return this.mcpServerType;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable ToolDefinition toolDefinition;

		private @Nullable String toolType;

		private @Nullable String toolCallArguments;

		private @Nullable String toolCallResult;

		private @Nullable String mcpServerProtocol;

		private @Nullable String mcpServerType;

		private Builder() {
		}

		public Builder toolDefinition(ToolDefinition toolDefinition) {
			this.toolDefinition = toolDefinition;
			return this;
		}

		public Builder toolType(@Nullable String toolType) {
			this.toolType = toolType;
			return this;
		}

		public Builder toolCallArguments(@Nullable String toolCallArguments) {
			this.toolCallArguments = toolCallArguments;
			return this;
		}

		public Builder toolCallResult(@Nullable String toolCallResult) {
			this.toolCallResult = toolCallResult;
			return this;
		}

		public Builder mcpServerProtocol(String mcpServerProtocol) {
			this.mcpServerProtocol = mcpServerProtocol;
			return this;
		}

		public Builder mcpServerType(String mcpServerType) {
			this.mcpServerType = mcpServerType;
			return this;
		}

		public McpServerToolObservationContext build() {
			Assert.notNull(this.toolDefinition, "toolDefinition cannot be null");
			String mcpServerProtocol = this.mcpServerProtocol;
			Assert.hasText(mcpServerProtocol, "mcpServerProtocol cannot be empty");
			String mcpServerType = this.mcpServerType;
			Assert.hasText(mcpServerType, "mcpServerType cannot be empty");
			return new McpServerToolObservationContext(this.toolDefinition, this.toolType, this.toolCallArguments,
					this.toolCallResult, mcpServerProtocol, mcpServerType);
		}

	}

}
