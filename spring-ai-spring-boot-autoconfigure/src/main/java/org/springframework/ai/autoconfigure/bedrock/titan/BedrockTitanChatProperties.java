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
package org.springframework.ai.autoconfigure.bedrock.titan;

import org.springframework.ai.bedrock.titan.BedrockTitanChatOptions;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Bedrock Titan Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockTitanChatProperties.CONFIG_PREFIX)
public class BedrockTitanChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.titan.chat";

	/**
	 * Enable Bedrock Titan Chat Client. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Titan Chat generative name. Defaults to 'amazon.titan-text-express-v1'.
	 */
	private String model = TitanChatModel.TITAN_TEXT_EXPRESS_V1.id();

	@NestedConfigurationProperty
	private BedrockTitanChatOptions options = BedrockTitanChatOptions.builder().withTemperature(0.7).build();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public BedrockTitanChatOptions getOptions() {
		return options;
	}

	public void setOptions(BedrockTitanChatOptions options) {
		this.options = options;
	}

}
