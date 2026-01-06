/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.vertexai.autoconfigure.anthropic;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.vertexai.anthropic.api.VertexAiAnthropicApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Anthropic chat models on the Vertex AI platform.
 * <p>
 * Allows customization of chat model options such as model selection, max tokens, and
 * other Anthropic-specific parameters via the {@code spring.ai.vertex.ai.anthropic.chat}
 * prefix.
 */
@ConfigurationProperties(VertexAiAnthropicChatProperties.CONFIG_PREFIX)
public class VertexAiAnthropicChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.anthropic.chat";

	@NestedConfigurationProperty
	private final AnthropicChatOptions options = AnthropicChatOptions.builder()
		.model(VertexAiAnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getValue())
		.maxTokens(AnthropicChatModel.DEFAULT_MAX_TOKENS)
		.build();

	public AnthropicChatOptions getOptions() {
		return this.options;
	}

}
