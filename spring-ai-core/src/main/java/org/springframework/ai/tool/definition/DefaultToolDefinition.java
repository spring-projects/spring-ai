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

import org.springframework.ai.tool.util.ToolUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link ToolDefinition}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record DefaultToolDefinition(String name, String description, String inputSchema) implements ToolDefinition {

	public DefaultToolDefinition {
		Assert.hasText(name, "name cannot be null or empty");
		Assert.hasText(description, "description cannot be null or empty");
		Assert.hasText(inputSchema, "inputSchema cannot be null or empty");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String name;

		private String description;

		private String inputSchema;

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

		public DefaultToolDefinition build() {
			if (!StringUtils.hasText(description)) {
				description = ToolUtils.getToolDescriptionFromName(description);
			}
			return new DefaultToolDefinition(name, description, inputSchema);
		}

	}

}
