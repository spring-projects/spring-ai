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

package org.springframework.ai.tool.metadata;

/**
 * Default implementation of {@link ToolMetadata}.
 *
 * @param returnDirect whether the tool result should be returned directly or passed back
 * to the model
 * @param requiresConfirmation whether a call of the tool needs a person's confirmation
 * before it runs (since 2.1.0)
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record DefaultToolMetadata(boolean returnDirect, boolean requiresConfirmation) implements ToolMetadata {

	/**
	 * Create metadata for a tool that needs no confirmation.
	 * @param returnDirect whether the tool result should be returned directly or passed
	 * back to the model
	 */
	public DefaultToolMetadata(boolean returnDirect) {
		this(returnDirect, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private boolean returnDirect = false;

		private boolean requiresConfirmation = false;

		private Builder() {
		}

		public Builder returnDirect(boolean returnDirect) {
			this.returnDirect = returnDirect;
			return this;
		}

		/**
		 * Whether a call of the tool needs a person's confirmation before it runs.
		 * @param requiresConfirmation {@code true} to pause the tool-calling loop on a
		 * call of the tool
		 * @return this builder
		 * @since 2.1.0
		 */
		public Builder requiresConfirmation(boolean requiresConfirmation) {
			this.requiresConfirmation = requiresConfirmation;
			return this;
		}

		public ToolMetadata build() {
			return new DefaultToolMetadata(this.returnDirect, this.requiresConfirmation);
		}

	}

}
