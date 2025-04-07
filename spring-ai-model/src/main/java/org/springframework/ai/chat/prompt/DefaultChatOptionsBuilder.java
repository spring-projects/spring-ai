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

	protected DefaultChatOptions options;

	public DefaultChatOptionsBuilder() {
		this.options = new DefaultChatOptions();
	}

	protected DefaultChatOptionsBuilder(DefaultChatOptions options) {
		this.options = options;
	}

	public DefaultChatOptionsBuilder model(String model) {
		this.options.setModel(model);
		return this;
	}

	public DefaultChatOptionsBuilder frequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	public DefaultChatOptionsBuilder maxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return this;
	}

	public DefaultChatOptionsBuilder presencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return this;
	}

	public DefaultChatOptionsBuilder stopSequences(List<String> stop) {
		this.options.setStopSequences(stop);
		return this;
	}

	public DefaultChatOptionsBuilder temperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	public DefaultChatOptionsBuilder topK(Integer topK) {
		this.options.setTopK(topK);
		return this;
	}

	public DefaultChatOptionsBuilder topP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	public ChatOptions build() {
		return this.options.copy();
	}

}
