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

package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.model.Media;
import org.springframework.ai.model.MediaContent;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

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
public class AssistantMessage extends AbstractMessage implements MediaContent {

	private final List<ToolCall> toolCalls;

	protected final List<Media> media;

	public AssistantMessage(String content) {
		this(content, Map.of());
	}

	public AssistantMessage(String content, Map<String, Object> properties) {
		this(content, properties, List.of());
	}

	public AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls) {
		this(content, properties, toolCalls, List.of());
	}

	public AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls,
			List<Media> media) {
		super(MessageType.ASSISTANT, content, properties);
		Assert.notNull(toolCalls, "Tool calls must not be null");
		Assert.notNull(media, "Media must not be null");
		this.toolCalls = toolCalls;
		this.media = media;
	}

	public List<ToolCall> getToolCalls() {
		return this.toolCalls;
	}

	public boolean hasToolCalls() {
		return !CollectionUtils.isEmpty(this.toolCalls);
	}

	@Override
	public List<Media> getMedia() {
		return this.media;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.toolCalls, that.toolCalls) && Objects.equals(this.media, that.media);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.toolCalls, this.media);
	}

	@Override
	public String toString() {
		return "AssistantMessage [messageType=" + this.messageType + ", toolCalls=" + this.toolCalls + ", textContent="
				+ this.textContent + ", metadata=" + this.metadata + "]";
	}

	public record ToolCall(String id, String type, String name, String arguments) {

	}

}
