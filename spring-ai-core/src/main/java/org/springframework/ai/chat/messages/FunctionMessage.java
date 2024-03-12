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
 * The FunctionMessage class represents a message with a function content in a chat
 * application.
 */
public class FunctionMessage extends AbstractMessage {

	private FunctionMessage(final MessageType type, final String textContent, final List<Media> media,
			final Map<String, Object> properties) {
		super(type, textContent, media, properties);
	}

	/**
	 * Creates a new {@link FunctionMessageBuilder} instance.
	 * @return A new instance of FunctionMessageBuilder.
	 */
	public static FunctionMessageBuilder builder() {
		return new FunctionMessageBuilder();
	}

	/**
	 * Initializes a new {@link FunctionMessageBuilder} with settings from an existing
	 * {@link FunctionMessage} object.
	 * @param message The FunctionMessage object whose settings are to be used.
	 * @return A FunctionMessageBuilder instance initialized with the provided
	 * FunctionMessage settings.
	 */
	public static FunctionMessageBuilder builder(FunctionMessage message) {
		return builder().withContent(message.getContent()).withProperties(message.getProperties());
	}

	/**
	 * Builder for {@link FunctionMessage}. This builder creates function message object.
	 */
	public static class FunctionMessageBuilder extends AbstractMessageBuilder<FunctionMessageBuilder> {

		private FunctionMessageBuilder() {
			super(MessageType.FUNCTION);
		}

		public FunctionMessageBuilder withContent(final String content) {
			return super.withContent(content);
		}

		public FunctionMessageBuilder withProperties(final Map<String, Object> properties) {
			return super.withProperties(properties);
		}

		public FunctionMessage build() {
			return new FunctionMessage(this.messageType, this.textContent, this.media, this.properties);
		}

	}

}
