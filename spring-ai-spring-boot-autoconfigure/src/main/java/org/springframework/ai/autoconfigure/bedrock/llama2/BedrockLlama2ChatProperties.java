/*
 * Copyright 2023 - 2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.bedrock.llama2;

import org.springframework.ai.bedrock.llama2.BedrockLlama2ChatOptions;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Bedrock Llama2.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockLlama2ChatProperties.CONFIG_PREFIX)
public class BedrockLlama2ChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.llama2.chat";

	/**
	 * Enable Bedrock Llama2 chat client. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * The generative id to use. See the {@link Llama2ChatModel} for the supported models.
	 */
	private String model = Llama2ChatModel.LLAMA2_70B_CHAT_V1.id();

	@NestedConfigurationProperty
	private BedrockLlama2ChatOptions options = BedrockLlama2ChatOptions.builder()
		.withTemperature(0.7f)
		.withMaxGenLen(300)
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

	public BedrockLlama2ChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockLlama2ChatOptions options) {
		this.options = options;
	}

}
