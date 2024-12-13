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

import java.util.List;

/**
 * Implementation of {@link ChatOptions.Builder} to create {@link DefaultChatOptions}.
 */
public class DefaultChatOptionsBuilder<T extends DefaultChatOptionsBuilder<T>> implements ChatOptions.Builder<T> {

	private final DefaultChatOptions options = new DefaultChatOptions();

	protected T self() {
		return (T) this;
	}

	public T model(String model) {
		this.options.setModel(model);
		return self();
	}

	public T frequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return self();
	}

	public T maxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return self();
	}

	public T presencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return self();
	}

	public T stopSequences(List<String> stop) {
		this.options.setStopSequences(stop);
		return self();
	}

	public T temperature(Double temperature) {
		this.options.setTemperature(temperature);
		return self();
	}

	public T topK(Integer topK) {
		this.options.setTopK(topK);
		return self();
	}

	public T topP(Double topP) {
		this.options.setTopP(topP);
		return self();
	}

	public ChatOptions build() {
		return this.options;
	}

}
