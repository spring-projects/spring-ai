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

/**
 * Lets the generative know the content was generated as a response to the user. This role
 * indicates messages that the generative has previously generated in the conversation. By
 * including assistant messages in the series, you provide context to the generative about
 * prior exchanges in the conversation.
 */
public class AssistantMessage extends AbstractMessage {

	private AssistantMessage(final MessageType type, final String textContent, final List<MediaData> mediaData,
			final Map<String, Object> properties) {
		super(type, textContent, mediaData, properties);
	}

	/**
	 * Creates a new {@link AssistantMessageBuilder} instance.
	 * @return A new instance of AssistantMessageBuilder.
	 */
	public static AssistantMessageBuilder builder() {
		return new AssistantMessageBuilder();
	}

	/**
	 * Initializes a new {@link AssistantMessageBuilder} with settings from an existing
	 * {@link AssistantMessage} object.
	 * @param message The AssistantMessage object whose settings are to be used.
	 * @return A AssistantMessageBuilder instance initialized with the provided
	 * AssistantMessage settings.
	 */
	public static AssistantMessageBuilder builder(final AssistantMessage message) {
		return builder().withContent(message.getContent()).withProperties(message.getProperties());
	}

	/**
	 * Builder for {@link AssistantMessage}. This builder creates assistant message
	 * object.
	 */
	public static class AssistantMessageBuilder extends AbstractMessageBuilder<AssistantMessageBuilder> {

		private AssistantMessageBuilder() {
			super(MessageType.ASSISTANT);
		}

		public AssistantMessageBuilder withContent(final String content) {
			return super.withContent(content);
		}

		public AssistantMessageBuilder withProperties(final Map<String, Object> properties) {
			return super.withProperties(properties);
		}

		public AssistantMessage build() {
			return new AssistantMessage(this.messageType, this.textContent, this.mediaData, this.properties);
		}

	}

}
