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

package org.springframework.ai.model.tool;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link ToolExecutionResult}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record DefaultToolExecutionResult(List<Message> conversationHistory, boolean returnDirect,
		boolean continuousStream) implements ToolExecutionResult {

	public DefaultToolExecutionResult(List<Message> conversationHistory, boolean returnDirect) {
		this(conversationHistory, returnDirect, false);
	}

	public DefaultToolExecutionResult {
		Assert.notNull(conversationHistory, "conversationHistory cannot be null");
		Assert.noNullElements(conversationHistory, "conversationHistory cannot contain null elements");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private List<Message> conversationHistory = List.of();

		private boolean returnDirect;

		private boolean continuousStream;

		private Builder() {
		}

		public Builder conversationHistory(List<Message> conversationHistory) {
			this.conversationHistory = conversationHistory;
			return this;
		}

		public Builder returnDirect(boolean returnDirect) {
			this.returnDirect = returnDirect;
			return this;
		}

		public Builder continuousStream(boolean continuousStream) {
			this.continuousStream = continuousStream;
			return this;
		}

		public DefaultToolExecutionResult build() {
			return new DefaultToolExecutionResult(this.conversationHistory, this.returnDirect, this.continuousStream);
		}

	}

}
