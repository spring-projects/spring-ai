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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * A message of the type 'user' passed as input Messages with the user role are from the
 * end-user or developer. They represent questions, prompts, or any input that you want
 * the generative to respond to.
 */
public class UserMessage extends AbstractMessage {

	private UserMessage(final MessageType type, final String textContent, final List<Media> media,
			final Map<String, Object> properties) {
		super(type, textContent, media, properties);
	}

	/**
	 * Creates a new {@link UserMessageBuilder} instance.
	 * @return A new instance of UserMessageBuilder.
	 */
	public static UserMessageBuilder builder() {
		return new UserMessageBuilder();
	}

	/**
	 * Initializes a new {@link UserMessageBuilder} with settings from an existing
	 * {@link UserMessage} object.
	 * @param message The UserMessage object whose settings are to be used.
	 * @return A UserMessageBuilder instance initialized with the provided UserMessage
	 * settings.
	 */
	public static UserMessageBuilder builder(final UserMessage message) {
		return builder().withContent(message.getContent())
			.withMedia(message.getMedia())
			.withProperties(message.getProperties());
	}

	/**
	 * Builder for {@link UserMessage}. This builder creates system message object.
	 */
	public static class UserMessageBuilder extends AbstractMessageBuilder<UserMessageBuilder> {

		private UserMessageBuilder() {
			super(MessageType.USER);
		}

		public UserMessageBuilder withContent(final String content) {
			return super.withContent(content);
		}

		/**
		 * Loads the content from the given resource and sets it as the content of the
		 * message being built. This method is useful for setting the content of a message
		 * to the contents of a file or other resource.
		 * @param resource the Spring Resource object representing the source of the
		 * content
		 * @return this builder instance to allow for method chaining
		 * @throws RuntimeException if an I/O error occurs reading from the resource
		 */
		public UserMessageBuilder withResource(final Resource resource) {
			try (InputStream inputStream = resource.getInputStream()) {
				return super.withContent(StreamUtils.copyToString(inputStream, Charset.defaultCharset()));
			}
			catch (IOException ex) {
				throw new RuntimeException("Failed to read resource", ex);
			}
		}

		public UserMessageBuilder withMedia(final List<Media> mediaData) {
			return super.withMedia(mediaData);
		}

		public UserMessage build() {
			return new UserMessage(this.messageType, this.textContent, this.media, this.properties);
		}

	}

}
