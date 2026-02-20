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

package org.springframework.ai.deepseek;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

/**
 * @author Mark Pollack
 * @author Soby Chacko
 * @author Sun Yuhan
 */
public class DeepSeekAssistantMessage extends AssistantMessage {

	private @Nullable Boolean prefix;

	private @Nullable String reasoningContent;

	protected DeepSeekAssistantMessage(@Nullable String content, @Nullable String reasoningContent,
			@Nullable Boolean prefix, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media,
			boolean thought) {
		super(content, properties, toolCalls, media, thought);
		this.reasoningContent = reasoningContent;
		this.prefix = prefix;
	}

	public static DeepSeekAssistantMessage prefixAssistantMessage(@Nullable String content) {
		return prefixAssistantMessage(content, null);
	}

	public static DeepSeekAssistantMessage prefixAssistantMessage(@Nullable String content,
			@Nullable String reasoningContent) {
		return new DeepSeekAssistantMessage.Builder().content(content).reasoningContent(reasoningContent).build();
	}

	public @Nullable Boolean getPrefix() {
		return this.prefix;
	}

	public void setPrefix(Boolean prefix) {
		this.prefix = prefix;
	}

	public @Nullable String getReasoningContent() {
		return this.reasoningContent;
	}

	public void setReasoningContent(@Nullable String reasoningContent) {
		this.reasoningContent = reasoningContent;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DeepSeekAssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.reasoningContent, that.reasoningContent) && Objects.equals(this.prefix, that.prefix);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.prefix, this.reasoningContent);
	}

	@Override
	public String toString() {
		return "DeepSeekAssistantMessage [messageType=" + this.messageType + ", toolCalls=" + super.getToolCalls()
				+ ", textContent=" + this.textContent + ", reasoningContent=" + this.reasoningContent + ", prefix="
				+ this.prefix + ", metadata=" + this.metadata + "]";
	}

	public static final class Builder {

		private @Nullable String content;

		private boolean thought;

		private Map<String, Object> properties = Map.of();

		private List<ToolCall> toolCalls = List.of();

		private List<Media> media = List.of();

		private @Nullable Boolean prefix;

		private @Nullable String reasoningContent;

		public Builder content(@Nullable String content) {
			this.content = content;
			return this;
		}

		public Builder thought(boolean thought) {
			this.thought = thought;
			return this;
		}

		public Builder properties(Map<String, Object> properties) {
			this.properties = properties;
			return this;
		}

		public Builder toolCalls(List<ToolCall> toolCalls) {
			this.toolCalls = toolCalls;
			return this;
		}

		public Builder media(List<Media> media) {
			this.media = media;
			return this;
		}

		public Builder prefix(@Nullable Boolean prefix) {
			this.prefix = prefix;
			return this;
		}

		public Builder reasoningContent(@Nullable String reasoningContent) {
			this.reasoningContent = reasoningContent;
			return this;
		}

		public DeepSeekAssistantMessage build() {
			return new DeepSeekAssistantMessage(this.content, this.reasoningContent, this.prefix, this.properties,
					this.toolCalls, this.media, this.thought);
		}

	}

}
