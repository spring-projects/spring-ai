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

import org.springframework.util.Assert;

/**
 * A single ordered element of a message's content, allowing text and media to be
 * interleaved in a caller-defined sequence.
 * <p>
 * A message that carries text alongside media in two separate collections can only ever
 * be serialized as "all text, then all media". Some multimodal prompts depend on a finer
 * ordering: a per-page document prompt, for instance, needs each page's image to directly
 * follow that page's own text so the model associates the two. Expressing the content as
 * an ordered {@code List<ContentPart>} makes such prompts representable.
 * <p>
 * Providers whose wire format is itself an ordered list of parts (Google GenAI,
 * Anthropic, OpenAI, Amazon Bedrock, Mistral AI) map content parts one-to-one. Providers
 * with a flat wire format receive the content flattened back to text-plus-media, which is
 * the most their API can express.
 *
 * @author Dmitrii Erokhin
 * @since 2.0.1
 * @see TextPart
 * @see MediaPart
 */
public sealed interface ContentPart {

	/**
	 * Creates a text content part.
	 * @param text the text of this part; must not be null, but may be empty
	 * @return a new text part
	 */
	static ContentPart text(String text) {
		return new TextPart(text);
	}

	/**
	 * Creates a media content part.
	 * @param media the media of this part; must not be null
	 * @return a new media part
	 */
	static ContentPart media(Media media) {
		return new MediaPart(media);
	}

	/**
	 * A text fragment within a message's content.
	 * <p>
	 * Empty and whitespace-only text is permitted, so that content parts derived from a
	 * message built with blank text round-trip faithfully. Providers should skip blank
	 * text parts when serializing, since several model APIs reject empty text blocks.
	 *
	 * @param text the text of this part
	 * @since 2.0.1
	 */
	record TextPart(String text) implements ContentPart {

		public TextPart {
			Assert.notNull(text, "text cannot be null");
		}

	}

	/**
	 * A media fragment within a message's content.
	 *
	 * @param media the media of this part
	 * @since 2.0.1
	 */
	record MediaPart(Media media) implements ContentPart {

		public MediaPart {
			Assert.notNull(media, "media cannot be null");
		}

	}

}
