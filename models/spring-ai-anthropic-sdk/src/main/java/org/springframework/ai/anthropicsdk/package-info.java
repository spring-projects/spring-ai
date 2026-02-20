/*
 * Copyright 2023-2025 the original author or authors.
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

/**
 * Spring AI integration with Anthropic's Claude models using the official
 * <a href="https://github.com/anthropics/anthropic-sdk-java">Anthropic Java SDK</a>.
 *
 * <p>
 * This package provides a {@link org.springframework.ai.chat.model.ChatModel}
 * implementation that enables interaction with Claude models through Anthropic's Messages
 * API. The integration supports both synchronous and streaming conversations,
 * tool/function calling, and full observability through Micrometer.
 *
 * <p>
 * <b>Key Classes:</b>
 * <ul>
 * <li>{@link org.springframework.ai.anthropicsdk.AnthropicSdkChatModel} - Main chat model
 * implementation</li>
 * <li>{@link org.springframework.ai.anthropicsdk.AnthropicSdkChatOptions} - Configuration
 * options for chat requests</li>
 * </ul>
 *
 * <p>
 * <b>Quick Start:</b> <pre>{@code
 * AnthropicSdkChatModel chatModel = new AnthropicSdkChatModel(
 *     AnthropicSdkChatOptions.builder()
 *         .model("claude-sonnet-4-20250514")
 *         .maxTokens(1024)
 *         .build());
 *
 * ChatResponse response = chatModel.call(new Prompt("Hello, Claude!"));
 * }</pre>
 *
 * @since 2.0.0
 * @see org.springframework.ai.anthropicsdk.AnthropicSdkChatModel
 * @see org.springframework.ai.anthropicsdk.AnthropicSdkChatOptions
 */
@NullMarked
package org.springframework.ai.anthropicsdk;

import org.jspecify.annotations.NullMarked;
