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
package org.springframework.ai.autoconfigure.bedrock.mistral;

import org.springframework.ai.bedrock.mistral.BedrockMistralChatOptions;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Bedrock Mistral Chat autoconfiguration properties.
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
@ConfigurationProperties(BedrockMistralChatProperties.CONFIG_PREFIX)
public class BedrockMistralChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.mistral.chat";

	/**
	 * Enable Bedrock Mistral Chat Client. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Mistral Chat generative name. Defaults to
	 * 'mistral.mistral-large-2402-v1:0'.
	 */
	private String model = MistralChatBedrockApi.MistralChatModel.MISTRAL_LARGE.id();

	@NestedConfigurationProperty
	private BedrockMistralChatOptions options = BedrockMistralChatOptions.builder().build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public BedrockMistralChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockMistralChatOptions options) {
		this.options = options;
	}

}
