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

import java.util.Map;

/**
 * Lets the generative know the content was generated as a response to the user. This role
 * indicates messages that the generative has previously generated in the conversation. By
 * including assistant messages in the series, you provide context to the generative about
 * prior exchanges in the conversation.
 */
public class AssistantMessage extends AbstractMessage {

	private final String id;

	private final Integer index;

	private final boolean isCompleted;

	private final boolean isToolCallRequest;

	public AssistantMessage(String responseId, Integer index, boolean isCompleted, String content,
			Map<String, Object> properties) {

		this(responseId, index, isCompleted, content, properties, false);
	}

	public AssistantMessage(String responseId, Integer index, boolean isCompleted, String content,
			Map<String, Object> properties, boolean isToolCallRequest) {

		super(MessageType.ASSISTANT, content, properties);
		this.id = responseId;
		this.index = index;
		this.isCompleted = isCompleted;
		this.isToolCallRequest = isToolCallRequest;
	}

	public String getId() {
		return this.id;
	}

	public Integer getIndex() {
		return this.index;
	}

	public boolean isCompleted() {
		return this.isCompleted;
	}

	public boolean isToolCallRequest() {
		return this.isToolCallRequest;
	}

	@Override
	public String toString() {
		return "AssistantMessage [responseId=" + id + ", index=" + index + ", isCompleted=" + isCompleted
				+ ", messageType=" + messageType + ", textContent=" + textContent + ", mediaData=" + mediaData
				+ ", properties=" + properties + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String id;

		private Integer index = 0;

		private boolean isCompleted;

		private boolean isToolCallRequest = false;

		private String content;

		private Map<String, Object> properties = Map.of();

		public Builder withId(String id) {
			this.id = id;
			return this;
		}

		public Builder withIndex(Integer index) {
			this.index = index;
			return this;
		}

		public Builder withIsCompleted(boolean isCompleted) {
			this.isCompleted = isCompleted;
			return this;
		}

		public Builder withIsToolCallRequest(boolean isToolCallRequest) {
			this.isToolCallRequest = isToolCallRequest;
			return this;
		}

		public Builder withContent(String content) {
			this.content = content;
			return this;
		}

		public Builder withProperties(Map<String, Object> properties) {
			this.properties = properties;
			return this;
		}

		public AssistantMessage build() {
			return new AssistantMessage(this.id, this.index, this.isCompleted, this.content, this.properties,
					this.isToolCallRequest);
		}

	}

}
