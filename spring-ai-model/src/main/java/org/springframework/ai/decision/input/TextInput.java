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

package org.springframework.ai.decision.input;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.util.Assert;

/**
 * A {@link DecisionInput} made of a text and, optionally, attached media.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class TextInput implements DecisionInput {

	private final String text;

	private final List<Media> media;

	TextInput(String text, List<Media> media) {
		Assert.hasText(text, "text cannot be null or empty");
		Assert.notNull(media, "media cannot be null");
		this.text = text;
		this.media = List.copyOf(media);
	}

	public String getText() {
		return this.text;
	}

	public List<Media> getMedia() {
		return this.media;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TextInput that)) {
			return false;
		}
		return this.text.equals(that.text) && this.media.equals(that.media);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text, this.media);
	}

	@Override
	public String toString() {
		return "TextInput{text='" + this.text + "', media=" + this.media.size() + "}";
	}

}
