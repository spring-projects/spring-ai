/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * Configuration properties for Bedrock Converse.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(BedrockConverseProxyChatProperties.CONFIG_PREFIX)
public class BedrockConverseProxyChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.converse.chat";

	@NestedConfigurationProperty
	private BedrockChatOptions options = BedrockChatOptions.builder().temperature(0.7).maxTokens(300).build();

	public BedrockChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockChatOptions options) {
		Assert.notNull(options, "BedrockChatOptions must not be null");
		this.options = options;
	}

}
