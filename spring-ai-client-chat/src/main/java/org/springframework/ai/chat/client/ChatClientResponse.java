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

package org.springframework.ai.chat.client;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

/**
 * Represents a response returned by a {@link ChatClient}.
 *
 * @param chatResponse The response returned by the AI model
 * @param context The contextual data propagated through the execution chain
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record ChatClientResponse(@Nullable ChatResponse chatResponse, Map<String, @Nullable Object> context) {

	public ChatClientResponse {
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet(), "context keys cannot be null");
		Assert.noNullElements(context.values(), "context values cannot be null");
	}

	public ChatClientResponse copy() {
		return new ChatClientResponse(this.chatResponse, new HashMap<>(this.context));
	}

	public Builder mutate() {
		return new Builder().chatResponse(this.chatResponse).context(new HashMap<>(this.context));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable ChatResponse chatResponse;

		private final Map<String, @Nullable Object> context = new HashMap<>();

		private Builder() {
		}

		public Builder chatResponse(@Nullable ChatResponse chatResponse) {
			this.chatResponse = chatResponse;
			return this;
		}

		public Builder context(Map<String, ? extends @Nullable Object> context) {
			Assert.notNull(context, "context cannot be null");
			Assert.noNullElements(context.keySet(), "context keys cannot be null");
			Assert.noNullElements(context.values(), "context values cannot be null");
			this.context.putAll(context);
			return this;
		}

		public Builder context(String key, Object value) {
			Assert.notNull(key, "key cannot be null");
			Assert.notNull(value, "value cannot be null");
			this.context.put(key, value);
			return this;
		}

		public ChatClientResponse build() {
			return new ChatClientResponse(this.chatResponse, this.context);
		}

	}

}
