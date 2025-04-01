/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * A message of the type 'user' passed as input Messages with the user role are from the
 * end-user or developer. They represent questions, prompts, or any input that you want
 * the generative to respond to.
 */
public class UserMessage extends AbstractMessage implements MediaContent {

	protected final List<Media> media;

	public UserMessage(String textContent) {
		this(MessageType.USER, textContent, new ArrayList<>(), Map.of());
	}

	public UserMessage(Resource resource) {
		super(MessageType.USER, resource, Map.of());
		this.media = new ArrayList<>();
	}

	public UserMessage(String textContent, List<Media> media) {
		this(MessageType.USER, textContent, media, Map.of());
	}

	public UserMessage(String textContent, Media... media) {
		this(textContent, Arrays.asList(media));
	}

	public UserMessage(String textContent, Collection<Media> mediaList, Map<String, Object> metadata) {
		this(MessageType.USER, textContent, mediaList, metadata);
	}

	public UserMessage(MessageType messageType, String textContent, Collection<Media> media,
			Map<String, Object> metadata) {
		super(messageType, textContent, metadata);
		Assert.notNull(media, "media data must not be null");
		this.media = new ArrayList<>(media);
	}

	@Override
	public String toString() {
		return "UserMessage{" + "content='" + getText() + '\'' + ", properties=" + this.metadata + ", messageType="
				+ this.messageType + '}';
	}

	@Override
	public List<Media> getMedia() {
		return this.media;
	}

	@Override
	public String getText() {
		return this.textContent;
	}

}
