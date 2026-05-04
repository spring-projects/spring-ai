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

package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
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
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class AssistantMessage extends AbstractMessage implements MediaContent {

	private final List<ToolCall> toolCalls;

	protected final List<Media> media;

	public AssistantMessage(@Nullable String content) {
		this(content, Map.of(), List.of(), List.of());
	}

	protected AssistantMessage(@Nullable String content, Map<String, Object> properties, List<ToolCall> toolCalls,
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

	/**
	 * @return {@code true} if the message carries at least one finalized (non-partial)
	 * tool call. Streaming partial deltas do not count: the model has not yet committed
	 * to executing them, so callers gating tool execution, persistence, or advisor
	 * routing on this method are insulated from intermediate streaming frames.
	 */
	public boolean hasToolCalls() {
		if (CollectionUtils.isEmpty(this.toolCalls)) {
			return false;
		}
		for (ToolCall toolCall : this.toolCalls) {
			if (!toolCall.partial()) {
				return true;
			}
		}
		return false;
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

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Represents a tool call requested by the assistant.
	 *
	 * @param id the tool call identifier
	 * @param type the tool type (typically {@code "function"})
	 * @param name the tool / function name
	 * @param arguments the tool arguments. When {@code partial} is {@code true} this
	 * carries only the incremental fragment produced by the latest streaming chunk,
	 * otherwise it carries the complete arguments string.
	 * @param partial when {@code true} this {@code ToolCall} is a streaming delta and
	 * must not be executed as-is — downstream tool execution should only run on the
	 * non-partial {@code ToolCall} that the model emits once arguments are complete
	 */
	public record ToolCall(String id, String type, String name, String arguments, boolean partial) {

		public ToolCall(String id, String type, String name, String arguments) {
			this(id, type, name, arguments, false);
		}

	}

	public static final class Builder {

		private @Nullable String content;

		private Map<String, Object> properties = Map.of();

		private List<ToolCall> toolCalls = List.of();

		private List<Media> media = List.of();

		private Builder() {
		}

		public Builder content(@Nullable String content) {
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

		public AssistantMessage build() {
			return new AssistantMessage(this.content, this.properties, this.toolCalls, this.media);
		}

	}

}
