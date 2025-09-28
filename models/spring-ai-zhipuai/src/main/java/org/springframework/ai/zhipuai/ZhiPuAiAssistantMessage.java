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
 */
public class ZhiPuAiAssistantMessage extends AssistantMessage {

	/**
	 * The CoT content of the message.
	 */
	private String reasoningContent;

	public ZhiPuAiAssistantMessage(String content) {
		super(content);
	}

	public ZhiPuAiAssistantMessage(String content, String reasoningContent, Map<String, Object> properties,
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

}
