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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.util.ParsingUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link ToolDefinition}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record DefaultToolDefinition(String name, String description, String inputSchema,
		Map<String, Object> metadata) implements ToolDefinition {

	public DefaultToolDefinition {
		Assert.hasText(name, "name cannot be null or empty");
		Assert.hasText(description, "description cannot be null or empty");
		Assert.hasText(inputSchema, "inputSchema cannot be null or empty");
		metadata = Map.copyOf(metadata);
	}

	/**
	 * Constructor for backward compatibility without metadata.
	 */
	public DefaultToolDefinition(String name, String description, String inputSchema) {
		this(name, description, inputSchema, Collections.emptyMap());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String name;

		private @Nullable String description;

		private @Nullable String inputSchema;

		private Map<String, Object> metadata = new HashMap<>();

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

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = new HashMap<>(metadata);
			return this;
		}

		public Builder addMetadata(String key, Object value) {
			this.metadata.put(key, value);
			return this;
		}

		public ToolDefinition build() {
			Assert.state(this.name != null, "toolName cannot be null or empty");
			if (!StringUtils.hasText(this.description)) {
				this.description = ParsingUtils.reConcatenateCamelCase(this.name, " ");
			}
			Assert.state(this.description != null, "toolDescription cannot be null or empty");
			Assert.state(this.inputSchema != null, "inputSchema cannot be null or empty");
			return new DefaultToolDefinition(this.name, this.description, this.inputSchema, this.metadata);
		}

	}

}
