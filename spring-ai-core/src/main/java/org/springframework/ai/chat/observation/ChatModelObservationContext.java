/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.observation;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for chat model exchanges.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ChatModelObservationContext extends ModelObservationContext<Prompt, ChatResponse> {

	private final ChatModelRequestOptions requestOptions;

	ChatModelObservationContext(Prompt prompt, AiOperationMetadata operationMetadata,
			ChatModelRequestOptions requestOptions) {
		super(prompt, operationMetadata);
		Assert.notNull(requestOptions, "requestOptions cannot be null");
		this.requestOptions = requestOptions;
	}

	public ChatModelRequestOptions getRequestOptions() {
		return this.requestOptions;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Prompt prompt;

		private AiOperationMetadata operationMetadata;

		private ChatModelRequestOptions requestOptions;

		private Builder() {
		}

		public Builder prompt(Prompt prompt) {
			this.prompt = prompt;
			return this;
		}

		public Builder operationMetadata(AiOperationMetadata operationMetadata) {
			this.operationMetadata = operationMetadata;
			return this;
		}

		public Builder requestOptions(ChatModelRequestOptions requestOptions) {
			this.requestOptions = requestOptions;
			return this;
		}

		public ChatModelObservationContext build() {
			return new ChatModelObservationContext(prompt, operationMetadata, requestOptions);
		}

	}

}
