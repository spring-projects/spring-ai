/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.prompt.messages;

import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMessage implements Message {

	protected String content;

	/**
	 * Additional options for the message to influence the response, not a model map.
	 */
	protected Map<String, Object> properties = new HashMap<>();

	protected MessageType messageType;

	protected AbstractMessage() {

	}

	protected AbstractMessage(MessageType messageType, String content) {
		this.messageType = messageType;
		this.content = content;
	}

	protected AbstractMessage(MessageType messageType, String content, Map<String, Object> messageProperties) {
		this.messageType = messageType;
		this.content = content;
		this.properties = messageProperties;
	}

	protected AbstractMessage(MessageType messageType, Resource resource) {
		this.messageType = messageType;
		try (InputStream inputStream = resource.getInputStream()) {
			this.content = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read resource", ex);
		}
	}

	protected AbstractMessage(MessageType messageType, Resource resource, Map<String, Object> messageProperties) {
		this.messageType = messageType;
		this.properties = messageProperties;
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
	public Map<String, Object> getProperties() {
		return this.properties;
	}

	@Override
	public MessageType getMessageType() {
		return this.messageType;
	}

	@Override
	public String getMessageTypeValue() {
		return this.messageType.getValue();
	}

}
