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

package org.springframework.ai.model.azure.openai.autoconfigure;

import org.springframework.ai.azure.openai.AzureOpenAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Azure OpenAI image generation options.
 *
 * @author Benoit Moussaud
 * @since 1.0.0 M1
 */
@ConfigurationProperties(AzureOpenAiImageOptionsProperties.CONFIG_PREFIX)
public class AzureOpenAiImageOptionsProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.openai.image";

	/**
	 * Enable Azure OpenAI chat client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private AzureOpenAiImageOptions options = AzureOpenAiImageOptions.builder().build();

	public AzureOpenAiImageOptions getOptions() {
		return this.options;
	}

	public void setOptions(AzureOpenAiImageOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
