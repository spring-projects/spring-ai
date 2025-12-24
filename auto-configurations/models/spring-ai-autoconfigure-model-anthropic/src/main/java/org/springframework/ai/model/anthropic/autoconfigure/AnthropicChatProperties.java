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

package org.springframework.ai.model.anthropic.autoconfigure;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Anthropic Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @since 1.0.0
 */
@ConfigurationProperties(AnthropicChatProperties.CONFIG_PREFIX)
public class AnthropicChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.anthropic.chat";

	/**
	 * Client lever Ollama options. Use this property to configure generative temperature,
	 * topK and topP and alike parameters. The null values are ignored defaulting to the
	 * generative's defaults.
	 */
	@NestedConfigurationProperty
	private final AnthropicChatOptions options = AnthropicChatOptions.builder()
		.model(AnthropicChatModel.DEFAULT_MODEL_NAME)
		.maxTokens(AnthropicChatModel.DEFAULT_MAX_TOKENS)
		.build();

	public AnthropicChatOptions getOptions() {
		return this.options;
	}

}
