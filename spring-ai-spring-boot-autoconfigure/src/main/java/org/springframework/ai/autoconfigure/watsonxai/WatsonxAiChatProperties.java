/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.watsonxai;

import java.util.List;

import org.springframework.ai.watsonx.WatsonxAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Chat properties for Watsonx.AI Chat.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @since 1.0.0
 */
@ConfigurationProperties(WatsonxAiChatProperties.CONFIG_PREFIX)
public class WatsonxAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.watsonx.ai.chat";

	/**
	 * Enable Watsonx.AI chat model.
	 */
	private boolean enabled = true;

	/**
	 * Watsonx AI generative options.
	 */
	@NestedConfigurationProperty
	private WatsonxAiChatOptions options = WatsonxAiChatOptions.builder()
		.model("google/flan-ul2")
		.temperature(0.7)
		.topP(1.0)
		.topK(50)
		.decodingMethod("greedy")
		.maxNewTokens(20)
		.minNewTokens(0)
		.repetitionPenalty(1.0)
		.stopSequences(List.of())
		.build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public WatsonxAiChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(WatsonxAiChatOptions options) {
		this.options = options;
	}

}
