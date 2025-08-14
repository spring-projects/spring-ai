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

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Represents a response returned by a {@link ChatClient}.
 *
 * @param chatResponse The response returned by the AI model
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record ChatClientResponse(@Nullable ChatResponse chatResponse) {

	public ChatClientResponse {
	}

	public ChatClientResponse copy() {
		if (this.chatResponse == null) {
			return new ChatClientResponse(null);
		}
		Map<String, Object> copiedContext = new HashMap<>(this.chatResponse.getContext());
		ChatResponse copiedChatResponse = new ChatResponse(
				this.chatResponse.getGenerations(),
				this.chatResponse.getMetadata(),
				copiedContext
		);
		return new ChatClientResponse(copiedChatResponse);
	}


	public Builder mutate() {
		Builder builder = new Builder().chatResponse(this.chatResponse);
		if (this.chatResponse != null && this.chatResponse.getContext() != null) {
			builder.context(new HashMap<>(this.chatResponse.getContext()));
		}
		return builder;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatResponse chatResponse;

		private Map<String, Object> context = new HashMap<>();

		private Builder() {
		}

		public Builder chatResponse(@Nullable ChatResponse chatResponse) {
			this.chatResponse = chatResponse;
			return this;
		}

		public Builder context(Map<String, Object> context) {
			Assert.notNull(context, "context cannot be null");
			this.context.putAll(context);
			return this;
		}


		// In Builder class
		public ChatClientResponse build() {
			if (this.chatResponse == null) {
				return new ChatClientResponse(null);
			}
			Map<String, Object> mergedContext = new HashMap<>(this.chatResponse.getContext());
			mergedContext.putAll(this.context);
			ChatResponse newChatResponse = new ChatResponse(
					this.chatResponse.getGenerations(),
					this.chatResponse.getMetadata(),
					mergedContext
			);
			return new ChatClientResponse(newChatResponse);
		}
	}

}
