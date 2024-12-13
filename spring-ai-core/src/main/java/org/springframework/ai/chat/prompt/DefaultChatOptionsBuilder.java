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
public class DefaultChatOptionsBuilder implements ChatOptions.Builder {

	private final DefaultChatOptions options = new DefaultChatOptions();

	public ChatOptions.Builder model(String model) {
		this.options.setModel(model);
		return this;
	}

	public ChatOptions.Builder frequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	public ChatOptions.Builder maxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return this;
	}

	public ChatOptions.Builder presencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return this;
	}

	public ChatOptions.Builder stopSequences(List<String> stop) {
		this.options.setStopSequences(stop);
		return this;
	}

	public ChatOptions.Builder temperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	public ChatOptions.Builder topK(Integer topK) {
		this.options.setTopK(topK);
		return this;
	}

	public ChatOptions.Builder topP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	public ChatOptions build() {
		return this.options;
	}

}
