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
package org.springframework.ai.autoconfigure.bedrock.anthropic;

import java.util.List;

import org.springframework.ai.bedrock.anthropic.AnthropicChatOptions;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * Configuration properties for Bedrock Anthropic.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockAnthropicChatProperties.CONFIG_PREFIX)
public class BedrockAnthropicChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.anthropic.chat";

	/**
	 * Enable Bedrock Anthropic chat model. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * The generative id to use. See the {@link AnthropicChatModel} for the supported
	 * models.
	 */
	private String model = AnthropicChatModel.CLAUDE_V2.id();

	@NestedConfigurationProperty
	private AnthropicChatOptions options = AnthropicChatOptions.builder()
		.withTemperature(0.7f)
		.withMaxTokensToSample(300)
		.withTopK(10)
		.withStopSequences(List.of("\n\nHuman:"))
		.build();

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

	public AnthropicChatOptions getOptions() {
		return options;
	}

	public void setOptions(AnthropicChatOptions options) {
		Assert.notNull(options, "AnthropicChatOptions must not be null");
		Assert.notNull(options.getTemperature(), "AnthropicChatOptions.temperature must not be null");

		this.options = options;
	}

}
