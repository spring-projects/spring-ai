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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * The AbstractMessage class is an abstract implementation of the Message interface. It
 * provides a base implementation for message content, media attachments, properties, and
 * message type.
 *
 * @see Message
 */
public abstract class AbstractMessage implements Message {

	protected final MessageType messageType;

	protected final String content;

	protected final List<Media> media;

	/**
	 * Additional options for the message to influence the response, not a generative map.
	 */
	protected final Map<String, Object> properties;

	protected AbstractMessage(MessageType messageType) {
		this(messageType, "");
	}

	protected AbstractMessage(MessageType messageType, String content) {
		this(messageType, content, Map.of());
	}

	protected AbstractMessage(MessageType messageType, String content, Map<String, Object> messageProperties) {
		Assert.notNull(messageType, "Message type must not be null");
		this.messageType = messageType;
		this.content = content;
		this.media = new ArrayList<>();
		this.properties = messageProperties;
	}

	protected AbstractMessage(MessageType messageType, String content, List<Media> media) {
		this(messageType, content, media, Map.of());
	}

	protected AbstractMessage(MessageType messageType, String content, List<Media> media,
							  Map<String, Object> messageProperties) {

		Assert.notNull(messageType, "Message type must not be null");
		Assert.notNull(content, "Content must not be null");
		Assert.notNull(media, "media data must not be null");

		this.messageType = messageType;
		this.content = content;
		this.media = new ArrayList<>(media);
		this.properties = messageProperties;
	}

	protected AbstractMessage(MessageType messageType, Resource resource) {
		this(messageType, resource, Collections.emptyMap());
	}

	@SuppressWarnings("null")
	protected AbstractMessage(MessageType messageType, Resource resource, Map<String, Object> messageProperties) {
		Assert.notNull(messageType, "Message type must not be null");
		Assert.notNull(resource, "Resource must not be null");

		this.messageType = messageType;
		this.properties = messageProperties;
		this.media = new ArrayList<>();

		try (InputStream inputStream = resource.getInputStream()) {
			this.content = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read resource", ex);
		}
	}

	@Override
	public String getContent() {
		return this.content;
	}

	@Override
	public List<Media> getMedia() {
		return this.media;
	}

	@Override
	public Map<String, Object> getProperties() {
		return this.properties;
	}

	@Override
	public MessageType getMessageType() {
		return this.messageType;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((media == null) ? 0 : media.hashCode());
		result = prime * result + ((properties == null) ? 0 : properties.hashCode());
		result = prime * result + ((messageType == null) ? 0 : messageType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractMessage other = (AbstractMessage) obj;
		if (media == null) {
			if (other.media != null)
				return false;
		}
		else if (!media.equals(other.media))
			return false;
		if (properties == null) {
			if (other.properties != null)
				return false;
		}
		else if (!properties.equals(other.properties))
			return false;
		if (messageType != other.messageType)
			return false;
		return true;
	}

}
