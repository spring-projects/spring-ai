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

package org.springframework.ai.autoconfigure.bedrock.jurrasic2;

import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatOptions;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Bedrock Ai21Jurassic2.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
@ConfigurationProperties(BedrockAi21Jurassic2ChatProperties.CONFIG_PREFIX)
public class BedrockAi21Jurassic2ChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.jurassic2.chat";

	/**
	 * Enable Bedrock Ai21Jurassic2 chat model. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * The generative id to use. See the {@link Ai21Jurassic2ChatModel} for the supported
	 * models.
	 */
	private String model = Ai21Jurassic2ChatModel.AI21_J2_MID_V1.id();

	@NestedConfigurationProperty
	private BedrockAi21Jurassic2ChatOptions options = BedrockAi21Jurassic2ChatOptions.builder()
		.withTemperature(0.7)
		.withMaxTokens(500)
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

	public BedrockAi21Jurassic2ChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockAi21Jurassic2ChatOptions options) {
		this.options = options;
	}

}
