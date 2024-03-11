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

import org.springframework.util.Assert;

/**
 * This abstract class represents the foundation for all message types within the chat
 * system. It encapsulates common properties such as message type, content, associated
 * media data, and additional properties that might influence the chat response.
 *
 * Implementations of this class are expected to be immutable to ensure thread safety and
 * predictability.
 *
 * @author youngmon
 */
public abstract class AbstractMessage implements Message {

	protected final MessageType messageType;

	protected final String textContent;

	protected final List<MediaData> mediaData;

	/**
	 * Additional options for the message to influence the response, not a generative map.
	 */
	protected final Map<String, Object> properties;

	protected AbstractMessage(final MessageType messageType, final String textContent, final List<MediaData> mediaData,
			final Map<String, Object> messageProperties) {

		Assert.notNull(messageType, "Message type must not be null");
		Assert.notNull(textContent, "Content must not be null");

		this.messageType = messageType;
		this.textContent = textContent;
		this.mediaData = mediaData;
		this.properties = messageProperties;
	}

	@Override
	public String getContent() {
		return this.textContent;
	}

	@Override
	public List<MediaData> getMediaData() {
		return this.mediaData;
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
		result = prime * result + ((mediaData == null) ? 0 : mediaData.hashCode());
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
		if (mediaData == null) {
			if (other.mediaData != null)
				return false;
		}
		else if (!mediaData.equals(other.mediaData))
			return false;
		if (properties == null) {
			if (other.properties != null)
				return false;
		}
		else if (!properties.equals(other.properties))
			return false;
		return messageType == other.messageType;
	}

	@Override
	public String toString() {
		return String.format("%s{content='%s', properties=%s, messageType=%s}", getClass().getSimpleName(),
				getContent(), getProperties().toString(), getMessageType());
	}

}
