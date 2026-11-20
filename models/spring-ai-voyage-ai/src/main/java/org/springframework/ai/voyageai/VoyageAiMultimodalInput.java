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

package org.springframework.ai.voyageai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.util.Assert;

/**
 * One input of a Voyage AI multimodal embedding request: an interleaved sequence of text
 * and media (images, videos) that is embedded into a single vector.
 *
 * <p>
 * A {@link Document} holds either text or media, so it can only express an input of one
 * part. Use the {@link #builder() builder} to combine several parts, for example a
 * caption and the image it belongs to.
 *
 * @param id identifies the input in the metadata of the resulting embedding
 * @param parts the parts, in order, not empty
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public record VoyageAiMultimodalInput(String id, List<Part> parts) {

	public VoyageAiMultimodalInput {
		Assert.hasText(id, "id must not be empty");
		Assert.notEmpty(parts, "An input needs at least one part");
		parts = List.copyOf(parts);
	}

	/**
	 * Create an input from a document, keeping its id.
	 * @param document a document holding text or media
	 * @return the input
	 */
	public static VoyageAiMultimodalInput of(Document document) {
		Assert.notNull(document, "document must not be null");
		Media media = document.getMedia();
		return new VoyageAiMultimodalInput(document.getId(),
				List.of((media != null) ? Part.of(media) : Part.of(requireText(document))));
	}

	private static String requireText(Document document) {
		String text = document.getText();
		Assert.hasText(text, "A document to embed needs text or media");
		return text;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A piece of an input: text or a medium.
	 *
	 * @param text the text, when this part is text
	 * @param media the medium, when this part is an image or a video
	 */
	public record Part(@Nullable String text, @Nullable Media media) {

		public Part {
			Assert.isTrue(text != null ^ media != null, "A part is either text or media");
			Assert.isTrue(text == null || !text.isBlank(), "The text of a part must not be blank");
		}

		public static Part of(String text) {
			return new Part(text, null);
		}

		public static Part of(Media media) {
			return new Part(null, media);
		}

	}

	/**
	 * Builder of a {@link VoyageAiMultimodalInput}.
	 */
	public static final class Builder {

		private String id = UUID.randomUUID().toString();

		private final List<Part> parts = new ArrayList<>();

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder text(String text) {
			this.parts.add(Part.of(text));
			return this;
		}

		public Builder media(Media media) {
			this.parts.add(Part.of(media));
			return this;
		}

		public VoyageAiMultimodalInput build() {
			return new VoyageAiMultimodalInput(this.id, this.parts);
		}

	}

}
