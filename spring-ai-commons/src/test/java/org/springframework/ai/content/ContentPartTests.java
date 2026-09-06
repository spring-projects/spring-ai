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

package org.springframework.ai.content;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ContentPart}.
 *
 * @author Dmitrii Erokhin
 */
class ContentPartTests {

	private static Media testMedia() {
		return new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/image.png"));
	}

	@Test
	void textFactoryCreatesTextPart() {
		ContentPart part = ContentPart.text("Hello, world!");

		assertThat(part).isInstanceOf(ContentPart.TextPart.class);
		assertThat(((ContentPart.TextPart) part).text()).isEqualTo("Hello, world!");
	}

	@Test
	void mediaFactoryCreatesMediaPart() {
		Media media = testMedia();

		ContentPart part = ContentPart.media(media);

		assertThat(part).isInstanceOf(ContentPart.MediaPart.class);
		assertThat(((ContentPart.MediaPart) part).media()).isSameAs(media);
	}

	@Test
	void textPartRejectsNullText() {
		assertThatThrownBy(() -> new ContentPart.TextPart(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null");
	}

	@Test
	void mediaPartRejectsNullMedia() {
		assertThatThrownBy(() -> new ContentPart.MediaPart(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("media cannot be null");
	}

	@Test
	void textPartAllowsEmptyAndBlankText() {
		// Blank text parts must be representable so that content derived from a message
		// built with blank text round-trips unchanged. Providers skip them when
		// serializing.
		assertThat(new ContentPart.TextPart("").text()).isEmpty();
		assertThat(new ContentPart.TextPart("   \t\n  ").text()).isEqualTo("   \t\n  ");
	}

	@Test
	void textPartsWithEqualTextAreEqual() {
		assertThat(ContentPart.text("same")).isEqualTo(ContentPart.text("same"))
			.hasSameHashCodeAs(ContentPart.text("same"));
		assertThat(ContentPart.text("one")).isNotEqualTo(ContentPart.text("two"));
	}

	@Test
	void mediaPartEqualityFollowsMediaIdentity() {
		Media media = testMedia();

		// Media does not override equals/hashCode, so MediaPart equality is necessarily
		// identity-based on the wrapped Media. Pinned here so the limitation is visible:
		// value-based equality requires Media#equals first.
		assertThat(ContentPart.media(media)).isEqualTo(ContentPart.media(media));
		assertThat(ContentPart.media(testMedia())).isNotEqualTo(ContentPart.media(testMedia()));
	}

}
