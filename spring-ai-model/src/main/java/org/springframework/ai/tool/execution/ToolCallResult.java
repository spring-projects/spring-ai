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

package org.springframework.ai.tool.execution;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Represents the result of a tool call, including optional metadata.
 *
 * @author Wada Yasuhiro
 * @since 1.1.0
 */
public final class ToolCallResult {

	private final @Nullable String content;

	private final Map<String, Object> metadata;

	private ToolCallResult(@Nullable String content, Map<String, Object> metadata) {
		this.content = content;
		this.metadata = Map.copyOf(metadata);
	}

	public @Nullable String content() {
		return this.content;
	}

	public Map<String, Object> metadata() {
		return this.metadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String content;

		private Map<String, Object> metadata = Map.of();

		private Builder() {
		}

		public Builder content(@Nullable String content) {
			this.content = content;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public ToolCallResult build() {
			return new ToolCallResult(this.content, this.metadata);
		}

	}

}
