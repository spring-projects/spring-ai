/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.image;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;

public class ImageMessage implements MediaContent {

	private final String text;

	private @Nullable Float weight;

	private final List<Media> media;

	private final Map<String, Object> metadata;

	public ImageMessage(String text) {
		this(text, null, new ArrayList<>(), Map.of());
	}

	public ImageMessage(String text, @Nullable Float weight) {
		this(text, weight, new ArrayList<>(), Map.of());
	}

	/**
	 * Create a new {@code ImageMessage}.
	 * @param text the text of the message
	 * @param weight the weight of the message, or {@code null} if not set
	 * @param media the media associated with the message
	 * @param metadata the metadata associated with the message
	 * @since 2.0.1
	 */
	public ImageMessage(String text, @Nullable Float weight, Collection<Media> media, Map<String, Object> metadata) {
		this.text = text;
		this.weight = weight;
		this.media = new ArrayList<>(media);
		this.metadata = new HashMap<>(metadata);
	}

	@Override
	public String getText() {
		return this.text;
	}

	public @Nullable Float getWeight() {
		return this.weight;
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
		return "ImageMessage{" + "text='" + this.text + '\'' + ", weight=" + this.weight + ", media=" + this.media
				+ ", metadata=" + this.metadata + '}';
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ImageMessage that)) {
			return false;
		}
		return Objects.equals(this.text, that.text) && Objects.equals(this.weight, that.weight)
				&& Objects.equals(this.media, that.media) && Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text, this.weight, this.media, this.metadata);
	}

}
