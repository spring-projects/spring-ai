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

package org.springframework.ai.tool.definition;

import org.springframework.ai.util.ParsingUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Extended implementation of {@link ToolDefinition} that preserves the original MCP tool
 * name and client name in addition to the prefixed name.
 *
 * @author Danny Sortino
 */
public record McpToolDefinition(String name, String description, String inputSchema, String toolName,
		String clientName) implements ToolDefinition {

	public McpToolDefinition {
		Assert.hasText(name, "name cannot be null or empty");
		Assert.hasText(description, "description cannot be null or empty");
		Assert.hasText(inputSchema, "inputSchema cannot be null or empty");
		Assert.hasText(toolName, "toolName cannot be null or empty");
		Assert.hasText(clientName, "clientName cannot be null or empty");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String name;

		private String description;

		private String inputSchema;

		private String toolName;

		private String clientName;

		private Builder() {
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder inputSchema(String inputSchema) {
			this.inputSchema = inputSchema;
			return this;
		}

		public Builder toolName(String toolName) {
			this.toolName = toolName;
			return this;
		}

		public Builder clientName(String clientName) {
			this.clientName = clientName;
			return this;
		}

		public McpToolDefinition build() {
			if (!StringUtils.hasText(this.description)) {
				Assert.hasText(this.toolName, "toolName cannot be null or empty");
				this.description = ParsingUtils.reConcatenateCamelCase(this.toolName, " ");
			}
			return new McpToolDefinition(this.name, this.description, this.inputSchema, this.toolName, this.clientName);
		}

	}

}
