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

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

/**
 * Represents a request processed by a {@link ChatClient} that ultimately is used to build
 * a {@link Prompt} to be sent to an AI model.
 *
 * @param prompt The prompt to be sent to the AI model
 * @param context The contextual data through the execution chain
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record ChatClientRequest(Prompt prompt, Map<String, Object> context) {

	public ChatClientRequest {
		Assert.notNull(prompt, "prompt cannot be null");
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet(), "context keys cannot be null");
	}

	public ChatClientRequest copy() {
		return new ChatClientRequest(this.prompt.copy(), new HashMap<>(this.context));
	}

	public Builder mutate() {
		return new Builder().prompt(this.prompt.copy()).context(new HashMap<>(this.context));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Prompt prompt;

		private Map<String, Object> context = new HashMap<>();

		private Builder() {
		}

		public Builder prompt(Prompt prompt) {
			Assert.notNull(prompt, "prompt cannot be null");
			this.prompt = prompt;
			return this;
		}

		public Builder context(Map<String, Object> context) {
			Assert.notNull(context, "context cannot be null");
			this.context.putAll(context);
			return this;
		}

		public Builder context(String key, Object value) {
			Assert.notNull(key, "key cannot be null");
			this.context.put(key, value);
			return this;
		}

		public ChatClientRequest build() {
			return new ChatClientRequest(this.prompt, this.context);
		}

	}

}
