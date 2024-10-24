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

package org.springframework.ai.autoconfigure.azure.openai;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(AzureOpenAiChatProperties.CONFIG_PREFIX)
public class AzureOpenAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.openai.chat";

	public static final String DEFAULT_DEPLOYMENT_NAME = "gpt-4o";

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	/**
	 * Enable Azure OpenAI chat model.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder()
		.withDeploymentName(DEFAULT_DEPLOYMENT_NAME)
		.withTemperature(DEFAULT_TEMPERATURE)
		.build();

	public AzureOpenAiChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(AzureOpenAiChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
