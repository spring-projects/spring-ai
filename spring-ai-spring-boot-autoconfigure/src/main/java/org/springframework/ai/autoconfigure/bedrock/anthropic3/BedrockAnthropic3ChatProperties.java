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
package org.springframework.ai.autoconfigure.bedrock.anthropic3;

import org.springframework.ai.bedrock.anthropic3.Anthropic3ChatOptions;
import org.springframework.ai.bedrock.anthropic3.BedrockAnthropic3ChatModel.Anthropic3ChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * Configuration properties for Bedrock Anthropic Claude 3.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 1.0.0
 */
@ConfigurationProperties(BedrockAnthropic3ChatProperties.CONFIG_PREFIX)
public class BedrockAnthropic3ChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.anthropic3.chat";

	/**
	 * Enable Bedrock Anthropic chat model. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * The generative id to use. See the {@link Anthropic3ChatModel} for the supported
	 * models.
	 */
	private String model = Anthropic3ChatModel.CLAUDE_V3_SONNET.id();

	@NestedConfigurationProperty
	private Anthropic3ChatOptions options = Anthropic3ChatOptions.builder().build();

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

	public Anthropic3ChatOptions getOptions() {
		return options;
	}

	public void setOptions(Anthropic3ChatOptions options) {
		Assert.notNull(options, "Anthropic3ChatOptions must not be null");

		this.options = options;
	}

}
