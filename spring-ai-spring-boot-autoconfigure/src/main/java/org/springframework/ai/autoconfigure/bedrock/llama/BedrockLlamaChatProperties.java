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

package org.springframework.ai.autoconfigure.bedrock.llama;

import org.springframework.ai.bedrock.llama.BedrockLlamaChatOptions;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Bedrock Llama.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockLlamaChatProperties.CONFIG_PREFIX)
public class BedrockLlamaChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.llama.chat";

	/**
	 * Enable Bedrock Llama chat model. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * The generative id to use. See the {@link LlamaChatModel} for the supported models.
	 */
	private String model = LlamaChatModel.LLAMA3_70B_INSTRUCT_V1.id();

	@NestedConfigurationProperty
	private BedrockLlamaChatOptions options = BedrockLlamaChatOptions.builder()
		.withTemperature(0.7)
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

	public BedrockLlamaChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockLlamaChatOptions options) {
		this.options = options;
	}

}
