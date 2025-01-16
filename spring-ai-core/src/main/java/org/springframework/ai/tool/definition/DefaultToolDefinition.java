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

package org.springframework.ai.tool.definition;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link ToolDefinition}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record DefaultToolDefinition(String name, String description, String inputTypeSchema) implements ToolDefinition {

	public DefaultToolDefinition {
		Assert.hasText(name, "name cannot be null or empty");
		Assert.hasText(description, "description cannot be null or empty");
		Assert.hasText(inputTypeSchema, "inputTypeSchema cannot be null or empty");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String name;

		private String description;

		private String inputTypeSchema;

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

		public Builder inputTypeSchema(String inputTypeSchema) {
			this.inputTypeSchema = inputTypeSchema;
			return this;
		}

		public DefaultToolDefinition build() {
			return new DefaultToolDefinition(name, description, inputTypeSchema);
		}

	}

}
