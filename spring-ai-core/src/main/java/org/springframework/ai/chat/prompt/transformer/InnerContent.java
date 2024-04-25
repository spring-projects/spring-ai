/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.prompt.transformer;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.model.Content;

/**
 * @author Christian Tzolov
 */
public class InnerContent implements Content {

	private final String content;

	private final List<Media> media;

	private final Map<String, Object> metadata;

	public InnerContent(String content) {
		this(content, Map.of());
	}

	public InnerContent(String content, Map<String, Object> metadata) {
		this(content, List.of(), metadata);
	}

	public InnerContent(String content, List<Media> media, Map<String, Object> metadata) {
		this.content = content;
		this.media = media;
		this.metadata = metadata;
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
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	@Override
	public String toString() {
		return "InnerContent [content=" + content + ", media=" + media + ", metadata=" + metadata + "]";
	}

}
