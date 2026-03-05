/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.prompt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@link ChatOptions.Builder} to create {@link DefaultChatOptions}.
 */
public class DefaultChatOptionsBuilder<B extends DefaultChatOptionsBuilder<B>> implements ChatOptions.Builder<B> {

	protected @Nullable String model;

	protected @Nullable Double frequencyPenalty;

	protected @Nullable Integer maxTokens;

	protected @Nullable Double presencePenalty;

	protected @Nullable List<String> stopSequences;

	protected @Nullable Double temperature;

	protected @Nullable Integer topK;

	protected @Nullable Double topP;

	public DefaultChatOptionsBuilder() {
	}

	@SuppressWarnings("unchecked")
	protected B self() {
		return (B) this;
	}

	@Override
	public B model(@Nullable String model) {
		this.model = model;
		return self();
	}

	@Override
	public B frequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
		return self();
	}

	@Override
	public B maxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
		return self();
	}

	@Override
	public B presencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
		return self();
	}

	@Override
	public B stopSequences(@Nullable List<String> stop) {
		if (stop != null) {
			this.stopSequences = new ArrayList<>(stop);
		}
		else {
			this.stopSequences = null;
		}
		return self();
	}

	@Override
	public B temperature(@Nullable Double temperature) {
		this.temperature = temperature;
		return self();
	}

	@Override
	public B topK(@Nullable Integer topK) {
		this.topK = topK;
		return self();
	}

	@Override
	public B topP(@Nullable Double topP) {
		this.topP = topP;
		return self();
	}

	@Override
	public B combineWith(ChatOptions.Builder<?> other) {
		if (other instanceof DefaultChatOptionsBuilder<?> that) {
			if (that.model != null) {
				this.model = that.model;
			}
			if (that.frequencyPenalty != null) {
				this.frequencyPenalty = that.frequencyPenalty;
			}
			if (that.maxTokens != null) {
				this.maxTokens = that.maxTokens;
			}
			if (that.presencePenalty != null) {
				this.presencePenalty = that.presencePenalty;
			}
			if (that.stopSequences != null) {
				this.stopSequences = that.stopSequences;
			}
			if (that.temperature != null) {
				this.temperature = that.temperature;
			}
			if (that.topK != null) {
				this.topK = that.topK;
			}
			if (that.topP != null) {
				this.topP = that.topP;
			}
		}
		return self();
	}

	public ChatOptions build() {
		// TODO: Assert.notNull() as required
		return new DefaultChatOptions(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty,
				this.stopSequences, this.temperature, this.topK, this.topP);
	}

}
