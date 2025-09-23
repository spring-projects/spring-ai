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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

public class DeepSeekAssistantMessage extends AssistantMessage {

	private Boolean prefix;

	private String reasoningContent;

	public DeepSeekAssistantMessage(String content) {
		super(content);
	}

	public DeepSeekAssistantMessage(String content, String reasoningContent) {
		super(content);
		this.reasoningContent = reasoningContent;
	}

	public DeepSeekAssistantMessage(String content, Map<String, Object> properties) {
		super(content, properties);
	}

	public DeepSeekAssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls) {
		super(content, properties, toolCalls);
	}

	public DeepSeekAssistantMessage(String content, String reasoningContent, Map<String, Object> properties,
			List<ToolCall> toolCalls) {
		this(content, reasoningContent, properties, toolCalls, List.of());
	}

	public DeepSeekAssistantMessage(String content, String reasoningContent, Map<String, Object> properties,
			List<ToolCall> toolCalls, List<Media> media) {
		super(content, properties, toolCalls, media);
		this.reasoningContent = reasoningContent;
	}

	public DeepSeekAssistantMessage(String content, String reasoningContent, Boolean prefix,
			Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media) {
		super(content, properties, toolCalls, media);
		this.reasoningContent = reasoningContent;
		this.prefix = prefix;
	}

	public static DeepSeekAssistantMessage prefixAssistantMessage(String context) {
		return prefixAssistantMessage(context, null);
	}

	public static DeepSeekAssistantMessage prefixAssistantMessage(String context, String reasoningContent) {
		return new DeepSeekAssistantMessage(context, reasoningContent);
	}

	public Boolean getPrefix() {
		return this.prefix;
	}

	public void setPrefix(Boolean prefix) {
		this.prefix = prefix;
	}

	public String getReasoningContent() {
		return this.reasoningContent;
	}

	public void setReasoningContent(String reasoningContent) {
		this.reasoningContent = reasoningContent;
	}

	@Override
	public boolean equals(Object o) {
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
		return "AssistantMessage [messageType=" + this.messageType + ", toolCalls=" + super.getToolCalls()
				+ ", textContent=" + this.textContent + ", reasoningContent=" + this.reasoningContent + ", prefix="
				+ this.prefix + ", metadata=" + this.metadata + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder extends AssistantMessage.Builder {

		private Boolean prefix;

		private String reasoningContent;

		@Override
		public Builder content(String content) {
			this.content = content;
			return this;
		}

		@Override
		public Builder properties(Map<String, Object> properties) {
			this.properties = properties;
			return this;
		}

		@Override
		public Builder toolCalls(List<ToolCall> toolCalls) {
			this.toolCalls = toolCalls;
			return this;
		}

		@Override
		public Builder media(List<Media> media) {
			this.media = media;
			return this;
		}

		public Builder prefix(Boolean prefix) {
			this.prefix = prefix;
			return this;
		}

		public Builder reasoningContent(String reasoningContent) {
			this.reasoningContent = reasoningContent;
			return this;
		}

		public DeepSeekAssistantMessage build() {
			return new DeepSeekAssistantMessage(this.content, this.reasoningContent, this.prefix, this.properties,
					this.toolCalls, this.media);
		}

	}

}
