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

package org.springframework.ai.mistralai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

/**
 * A Mistral AI specific implementation of {@link AssistantMessage} that supports
 * additional fields returned by Magistral reasoning models.
 *
 * <p>
 * Magistral models (like magistral-medium-latest and magistral-small-latest) return
 * thinking/reasoning content alongside the regular response content. This class captures
 * both the final response text and the intermediate reasoning process.
 * </p>
 *
 * @author Kyle Kreuter
 * @since 1.1.0
 */
public class MistralAiAssistantMessage extends AssistantMessage {

	/**
	 * The thinking/reasoning content from Magistral models. This contains the model's
	 * intermediate reasoning steps before producing the final response.
	 */
	private String thinkingContent;

	/**
	 * Constructs a new MistralAiAssistantMessage with all fields.
	 * @param content the main text content of the message
	 * @param thinkingContent the thinking/reasoning content from Magistral models
	 * @param properties additional metadata properties
	 * @param toolCalls list of tool calls requested by the model
	 * @param media list of media attachments
	 */
	protected MistralAiAssistantMessage(String content, String thinkingContent, Map<String, Object> properties,
			List<ToolCall> toolCalls, List<Media> media) {
		super(content, properties, toolCalls, media);
		this.thinkingContent = thinkingContent;
	}

	/**
	 * Returns the thinking/reasoning content from Magistral models.
	 * @return the thinking content, or null if not available
	 */
	public String getThinkingContent() {
		return this.thinkingContent;
	}

	/**
	 * Sets the thinking/reasoning content.
	 * @param thinkingContent the thinking content to set
	 * @return this instance for method chaining
	 */
	public MistralAiAssistantMessage setThinkingContent(String thinkingContent) {
		this.thinkingContent = thinkingContent;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MistralAiAssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.thinkingContent, that.thinkingContent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.thinkingContent);
	}

	@Override
	public String toString() {
		return "MistralAiAssistantMessage{" + "media=" + this.media + ", messageType=" + this.messageType
				+ ", metadata=" + this.metadata + ", thinkingContent='" + this.thinkingContent + '\''
				+ ", textContent='" + this.textContent + '\'' + '}';
	}

	/**
	 * Builder for creating MistralAiAssistantMessage instances.
	 */
	public static final class Builder {

		private String content;

		private Map<String, Object> properties = Map.of();

		private List<ToolCall> toolCalls = List.of();

		private List<Media> media = List.of();

		private String thinkingContent;

		/**
		 * Sets the main text content.
		 * @param content the content to set
		 * @return this builder
		 */
		public Builder content(String content) {
			this.content = content;
			return this;
		}

		/**
		 * Sets the metadata properties.
		 * @param properties the properties to set
		 * @return this builder
		 */
		public Builder properties(Map<String, Object> properties) {
			this.properties = properties;
			return this;
		}

		/**
		 * Sets the tool calls.
		 * @param toolCalls the tool calls to set
		 * @return this builder
		 */
		public Builder toolCalls(List<ToolCall> toolCalls) {
			this.toolCalls = toolCalls;
			return this;
		}

		/**
		 * Sets the media attachments.
		 * @param media the media to set
		 * @return this builder
		 */
		public Builder media(List<Media> media) {
			this.media = media;
			return this;
		}

		/**
		 * Sets the thinking/reasoning content from Magistral models.
		 * @param thinkingContent the thinking content to set
		 * @return this builder
		 */
		public Builder thinkingContent(String thinkingContent) {
			this.thinkingContent = thinkingContent;
			return this;
		}

		/**
		 * Builds the MistralAiAssistantMessage instance.
		 * @return a new MistralAiAssistantMessage
		 */
		public MistralAiAssistantMessage build() {
			return new MistralAiAssistantMessage(this.content, this.thinkingContent, this.properties, this.toolCalls,
					this.media);
		}

	}

}
