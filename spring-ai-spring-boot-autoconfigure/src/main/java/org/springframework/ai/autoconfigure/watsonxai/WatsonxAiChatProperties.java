/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.watsonxai;

import org.springframework.ai.watsonx.WatsonxAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Chat properties for Watsonx.AI Chat.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(WatsonxAiChatProperties.CONFIG_PREFIX)
public class WatsonxAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.watsonx.ai.chat";

	/**
	 * Enable Watsonx.AI chat client.
	 */
	private boolean enabled = true;

	/**
	 * Watsonx AI generative options.
	 */
	@NestedConfigurationProperty
	private WatsonxAiChatOptions options = WatsonxAiChatOptions.builder()
		.withModel("google/flan-ul2")
		.withTemperature(0.7f)
		.withTopP(1.0f)
		.withTopK(50)
		.withDecodingMethod("greedy")
		.withMaxNewTokens(20)
		.withMinNewTokens(0)
		.withRepetitionPenalty(1.0f)
		.withStopSequences(List.of())
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
