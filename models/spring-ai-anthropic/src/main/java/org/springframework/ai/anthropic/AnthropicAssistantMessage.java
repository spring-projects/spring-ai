/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.anthropic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.Assert;

/**
 * Anthropic assistant message that carries thinking content required for request replay.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 */
public final class AnthropicAssistantMessage extends AssistantMessage {

	static final String ANTHROPIC_THINKING_CONTENTS_PROPERTY = "anthropicThinkingContents";

	private final List<AnthropicThinkingContent> thinkingContents;

	private AnthropicAssistantMessage(@Nullable String content, Map<String, Object> properties,
			List<ToolCall> toolCalls, List<Media> media, List<AnthropicThinkingContent> thinkingContents) {
		super(content, anthropicThinkingProperties(properties, thinkingContents), List.copyOf(toolCalls),
				List.copyOf(media));
		this.thinkingContents = List.copyOf(thinkingContents);
	}

	/**
	 * Return the Anthropic thinking content attached to this assistant message.
	 * @return the thinking content
	 */
	public List<AnthropicThinkingContent> getThinkingContents() {
		return this.thinkingContents;
	}

	/**
	 * Whether this assistant message carries Anthropic thinking content.
	 * @return true if thinking content is present
	 */
	public boolean hasThinkingContents() {
		return !this.thinkingContents.isEmpty();
	}

	@Override
	public String toString() {
		return "AnthropicAssistantMessage [messageType=" + getMessageType() + ", toolCalls=" + getToolCalls()
				+ ", thinkingContents=" + this.thinkingContents.size() + "]";
	}

	/**
	 * Create a new builder for {@link AnthropicAssistantMessage}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	static Map<String, Object> anthropicThinkingProperties(Map<String, Object> properties,
			List<AnthropicThinkingContent> thinkingContents) {
		Assert.notNull(properties, "properties must not be null");
		Assert.notNull(thinkingContents, "thinkingContents must not be null");
		if (thinkingContents.isEmpty()) {
			return properties;
		}
		Map<String, Object> messageProperties = new HashMap<>(properties);
		messageProperties.put(ANTHROPIC_THINKING_CONTENTS_PROPERTY, List.copyOf(thinkingContents));
		return messageProperties;
	}

	/**
	 * Builder for {@link AnthropicAssistantMessage}.
	 */
	public static class Builder extends AssistantMessage.Builder<Builder> {

		private List<AnthropicThinkingContent> thinkingContents = List.of();

		/**
		 * Set the Anthropic thinking content carried by the assistant message.
		 * @param thinkingContents the thinking content
		 * @return the builder
		 */
		public Builder thinkingContents(List<AnthropicThinkingContent> thinkingContents) {
			Assert.notNull(thinkingContents, "thinkingContents must not be null");
			this.thinkingContents = thinkingContents;
			return this;
		}

		@Override
		public AnthropicAssistantMessage build() {
			return new AnthropicAssistantMessage(this.content, this.properties, this.toolCalls, this.media,
					this.thinkingContents);
		}

	}

}
