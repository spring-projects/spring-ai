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
 * A message of the type 'system' passed as input. The system message gives high level
 * instructions for the conversation. This role typically provides high-level instructions
 * for the conversation. For example, you might use a system message to instruct the
 * generative to behave like a certain character or to provide answers in a specific
 * format.
 */
public class SystemMessage extends AbstractMessage {

	private SystemMessage(final MessageType type, final String textContent, final List<Media> media,
			final Map<String, Object> properties) {
		super(type, textContent, media, properties);
	}

	/**
	 * Creates a new {@link SystemMessageBuilder} instance.
	 * @return A new instance of SystemMessageBuilder.
	 */
	public static SystemMessageBuilder builder() {
		return new SystemMessageBuilder();
	}

	/**
	 * Initializes a new {@link SystemMessageBuilder} with settings from an existing
	 * {@link SystemMessage} object.
	 * @param message The SystemMessage object whose settings are to be used.
	 * @return A SystemMessageBuilder instance initialized with the provided SystemMessage
	 * settings.
	 */
	public static SystemMessageBuilder builder(final SystemMessage message) {
		return builder().withContent(message.getContent());
	}

	/**
	 * Builder for {@link SystemMessage}. This builder creates system message object.
	 */
	public static class SystemMessageBuilder extends AbstractMessageBuilder<SystemMessageBuilder> {

		private SystemMessageBuilder() {
			super(MessageType.SYSTEM);
		}

		public SystemMessageBuilder withContent(final String content) {
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
		public SystemMessageBuilder withResource(final Resource resource) {
			try (InputStream inputStream = resource.getInputStream()) {
				return super.withContent(StreamUtils.copyToString(inputStream, Charset.defaultCharset()));
			}
			catch (IOException ex) {
				throw new RuntimeException("Failed to read resource", ex);
			}
		}

		public SystemMessage build() {
			return new SystemMessage(this.messageType, this.textContent, this.media, this.properties);
		}

	}

}
