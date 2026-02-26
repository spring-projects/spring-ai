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

package org.springframework.ai.tool.metadata;

/**
 * Default implementation of {@link ToolMetadata}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record DefaultToolMetadata(boolean returnDirect, boolean continuousStream) implements ToolMetadata {

	public DefaultToolMetadata(boolean returnDirect) {
		this(returnDirect, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private boolean returnDirect = false;

		private boolean continuousStream = false;

		private Builder() {
		}

		public Builder returnDirect(boolean returnDirect) {
			this.returnDirect = returnDirect;
			return this;
		}

		public Builder continuousStream(boolean continuousStream) {
			this.continuousStream = continuousStream;
			return this;
		}

		public ToolMetadata build() {
			return new DefaultToolMetadata(this.returnDirect, this.continuousStream);
		}

	}

}
