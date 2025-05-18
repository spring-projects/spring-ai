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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Lets the generative know the content was generated as a response to the user. This role
 * indicates messages that the generative has previously generated in the conversation. By
 * including assistant messages in the series, you provide context to the generative about
 * prior exchanges in the conversation.
 *
 * @author Jemin Huh
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class AssistantMessage extends AbstractMessage implements MediaContent {

	protected final List<ToolCall> toolCalls;

	protected final List<Media> media;

	public AssistantMessage(String content) {
		this(content, Map.of(), List.of(), List.of());
	}

	public AssistantMessage(Resource resource) {
		this(MessageUtils.readResource(resource));
	}

	protected AssistantMessage(String content, Map<String, Object> metadata, List<ToolCall> toolCalls,
			List<Media> media) {
		super(MessageType.ASSISTANT, content, metadata);
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

	public AssistantMessage copy() {
		return new Builder().text(getText())
			.metadata(Map.copyOf(getMetadata()))
			.toolCalls(List.copyOf(getToolCalls()))
			.media(List.copyOf(getMedia()))
			.build();
	}

	public AssistantMessage.Builder mutate() {
		return new Builder().text(getText())
			.metadata(Map.copyOf(getMetadata()))
			.toolCalls(List.copyOf(getToolCalls()))
			.media(List.copyOf(getMedia()));
	}

	public static AssistantMessage.Builder builder() {
		return new AssistantMessage.Builder();
	}

	public static class Builder {

		@Nullable
		private String textContent;

		@Nullable
		private Resource resource;

		private Map<String, Object> metadata = new HashMap<>();

		private List<ToolCall> toolCalls = new ArrayList<>();

		private List<Media> media = new ArrayList<>();

		public AssistantMessage.Builder text(String textContent) {
			this.textContent = textContent;
			return this;
		}

		public AssistantMessage.Builder text(Resource resource) {
			this.resource = resource;
			return this;
		}

		public AssistantMessage.Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public AssistantMessage.Builder toolCalls(List<ToolCall> toolCalls) {
			this.toolCalls = toolCalls;
			return this;
		}

		public AssistantMessage.Builder toolCalls(@Nullable ToolCall... toolCalls) {
			if (media != null) {
				this.toolCalls = Arrays.asList(toolCalls);
			}
			return this;
		}

		public AssistantMessage.Builder media(List<Media> media) {
			this.media = media;
			return this;
		}

		public AssistantMessage.Builder media(@Nullable Media... media) {
			if (media != null) {
				this.media = Arrays.asList(media);
			}
			return this;
		}

		public AssistantMessage build() {
			if (StringUtils.hasText(this.textContent) && this.resource != null) {
				throw new IllegalArgumentException("textContent and resource cannot be set at the same time");
			}
			else if (this.resource != null) {
				this.textContent = MessageUtils.readResource(this.resource);
			}
			return new AssistantMessage(this.textContent, this.metadata, this.toolCalls, this.media);
		}

	}

}
