/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.zhipuai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

/**
 * @author YunKui Lu
 * @author Sun Yuhan
 */
public class ZhiPuAiAssistantMessage extends AssistantMessage {

	/**
	 * The CoT content of the message.
	 */
	private String reasoningContent;

	protected ZhiPuAiAssistantMessage(String content, String reasoningContent, Map<String, Object> properties,
			List<ToolCall> toolCalls, List<Media> media) {
		super(content, properties, toolCalls, media);
		this.reasoningContent = reasoningContent;
	}

	public String getReasoningContent() {
		return this.reasoningContent;
	}

	public ZhiPuAiAssistantMessage setReasoningContent(String reasoningContent) {
		this.reasoningContent = reasoningContent;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ZhiPuAiAssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.reasoningContent, that.reasoningContent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.reasoningContent);
	}

	@Override
	public String toString() {
		return "ZhiPuAiAssistantMessage{" + "media=" + this.media + ", messageType=" + this.messageType + ", metadata="
				+ this.metadata + ", reasoningContent='" + this.reasoningContent + '\'' + ", textContent='"
				+ this.textContent + '\'' + '}';
	}

	public static final class Builder {

		private String content;

		private Map<String, Object> properties = Map.of();

		private List<ToolCall> toolCalls = List.of();

		private List<Media> media = List.of();

		private String reasoningContent;

		public Builder content(String content) {
			this.content = content;
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

		public Builder reasoningContent(String reasoningContent) {
			this.reasoningContent = reasoningContent;
			return this;
		}

		public ZhiPuAiAssistantMessage build() {
			return new ZhiPuAiAssistantMessage(this.content, this.reasoningContent, this.properties, this.toolCalls,
					this.media);
		}

	}

}
