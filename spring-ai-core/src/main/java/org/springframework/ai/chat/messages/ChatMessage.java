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
 * Represents a chat message in a chat application.
 *
 */
public class ChatMessage extends AbstractMessage {

	private ChatMessage(final MessageType type, final String textContent, final List<Media> media,
			final Map<String, Object> properties) {
		super(type, textContent, media, properties);
	}

	/**
	 * Creates a new {@link ChatMessageBuilder} instance.
	 * @return A new instance of ChatMessageBuilder.
	 */
	public static ChatMessageBuilder builder() {
		return new ChatMessageBuilder();
	}

	/**
	 * Initializes a new {@link ChatMessageBuilder} with settings from an existing
	 * {@link ChatMessage} object.
	 * @param message The ChatMessage object whose settings are to be used.
	 * @return A ChatMessageBuilder instance initialized with the provided ChatMessage
	 * settings.
	 */
	public static ChatMessageBuilder builder(final ChatMessage message) {
		return builder().withMessageType(message.getMessageType())
			.withContent(message.getContent())
			.withProperties(message.getProperties());
	}

	/**
	 * Initializes a {@link ChatMessageBuilder} with a message type determined by the
	 * provided role. This method is a convenient shortcut to start building a
	 * {@link ChatMessage} when the role is known. It internally calls {@link #builder()}
	 * to create a new builder instance and sets the message type by converting the role
	 * to a {@link MessageType} using {@link MessageType#valueOf(String)}.
	 *
	 * See {@link ChatMessageBuilder#withRole(String)} for more details on role handling.
	 * @param role a string that should match one of the {@link MessageType} enum
	 * constants, indicating the desired message type
	 * @return a {@link ChatMessageBuilder} instance with the initial message type set
	 * @throws IllegalArgumentException if the role does not correspond to any
	 * {@link MessageType} enum constants
	 */
	public static ChatMessageBuilder builder(final String role) {
		return builder().withMessageType(MessageType.valueOf(role));
	}

	/**
	 * Builder for {@link ChatMessage}. This builder creates chat message object.
	 */
	public static class ChatMessageBuilder extends AbstractMessageBuilder<ChatMessageBuilder> {

		private ChatMessageBuilder() {
			super();
		}

		/**
		 * Sets the message type based on the provided role. The role argument is expected
		 * to correspond to a valid enum value of {@link MessageType}. This method allows
		 * for the dynamic assignment of the message type, facilitating different
		 * behaviors or processing logic based on the message's role in the conversation.
		 *
		 * Note: This method internally converts the role string to a {@link MessageType}
		 * enum. It's important to ensure that the provided role string exactly matches
		 * one of the enum's constants. If the match is unsuccessful, an
		 * IllegalArgumentException will be thrown by {@link MessageType#valueOf(String)}.
		 * @param role a string representation of the message type, corresponding to the
		 * {@link MessageType} enum
		 * @return this builder instance to allow for method chaining
		 * @throws IllegalArgumentException if the provided role does not match any
		 * {@link MessageType} enum constants
		 */
		public ChatMessageBuilder withRole(final String role) {
			return super.withMessageType(MessageType.valueOf(role));
		}

		public ChatMessageBuilder withMessageType(final MessageType type) {
			return super.withMessageType(type);
		}

		public ChatMessageBuilder withContent(final String content) {
			return super.withContent(content);
		}

		public ChatMessageBuilder withProperties(final Map<String, Object> properties) {
			return super.withProperties(properties);
		}

		public ChatMessage build() {
			return new ChatMessage(this.messageType, this.textContent, this.media, this.properties);
		}

	}

}
