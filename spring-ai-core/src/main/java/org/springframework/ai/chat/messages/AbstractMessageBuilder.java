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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This abstract builder class provides a generic way to construct message objects of any
 * type. It follows the builder pattern to facilitate the step-by-step construction of
 * message objects while keeping the constructed object immutable.
 *
 * @param <T> the type of the concrete builder that extends this abstract builder, used to
 * enable method chaining with the correct return type.
 * @since 0.8.1
 * @author youngmon
 */
public abstract class AbstractMessageBuilder<T extends AbstractMessageBuilder<T>> {

	protected MessageType messageType;

	protected String textContent;

	protected List<Media> media = new ArrayList<>();

	/**
	 * Additional options for the message to influence the response, not a generative map.
	 */
	protected Map<String, Object> properties = new HashMap<>();

	protected AbstractMessageBuilder() {
	}

	protected AbstractMessageBuilder(final MessageType type) {
		this.messageType = type;
	}

	protected T withMessageType(final MessageType type) {
		this.messageType = type;
		return (T) this;
	}

	protected T withContent(final String content) {
		this.textContent = content;
		return (T) this;
	}

	protected T withProperties(final Map<String, Object> properties) {
		this.properties.putAll(properties);
		return (T) this;
	}

	protected T withMedia(final List<Media> media) {
		this.media.addAll(media);
		return (T) this;
	}

}
