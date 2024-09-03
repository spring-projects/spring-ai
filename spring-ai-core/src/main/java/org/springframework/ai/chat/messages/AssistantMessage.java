/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.util.Assert;

/**
 * Lets the generative know the content was generated as a response to the user. This role
 * indicates messages that the generative has previously generated in the conversation. By
 * including assistant messages in the series, you provide context to the generative about
 * prior exchanges in the conversation.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class AssistantMessage extends AbstractMessage {

	public record ToolCall(String id, String type, String name, String arguments) {
	}

	private final List<ToolCall> toolCalls;

	public AssistantMessage(String content) {
		this(content, Map.of());
	}

	public AssistantMessage(String content, Map<String, Object> properties) {
		this(content, properties, List.of());
	}

	public AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls) {
		super(MessageType.ASSISTANT, content, properties);
		Assert.notNull(toolCalls, "Tool calls must not be null");
		this.toolCalls = toolCalls;
	}

	@Override
	public String getContent() {
		return this.textContent;
	}

	public List<ToolCall> getToolCalls() {
		return this.toolCalls;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof AssistantMessage that))
			return false;
		if (!super.equals(o))
			return false;
		return Objects.equals(toolCalls, that.toolCalls);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), toolCalls);
	}

	@Override
	public String toString() {
		return "AssistantMessage [messageType=" + messageType + ", toolCalls=" + toolCalls + ", textContent="
				+ textContent + ", metadata=" + metadata + "]";
	}

}
