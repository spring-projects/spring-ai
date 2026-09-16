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

package org.springframework.ai.document;

/**
 * Common set of metadata keys used in {@link Document}s by {@link DocumentReader}s and
 * VectorStores.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum DocumentMetadata {

// @formatter:off

	/**
	 * Measure of distance between the document embedding and the query vector.
	 * The lower the distance, the more they are similar.
	 * It's the opposite of the similarity score.
	 */
	DISTANCE("distance"),

	/**
	 * Metadata key holding a pointer to content kept outside the vector store.
	 * <p>
	 * Use it when a row's embedding was computed from something that isn't stored
	 * in the row itself &mdash; an image, a video frame, an audio clip, or a
	 * document too large to inline. The vector is stored and stays searchable, but
	 * in place of the content the row keeps a reference to where the content really
	 * lives (for example an S3 URI, a CDN URL, or a database key). The store treats
	 * this reference as an opaque string and never resolves it; after a search
	 * returns the row, the application follows the pointer to fetch the content.
	 * <p>
	 * Such a row is created with an empty-text {@link Document} plus this key, and
	 * written with a caller-supplied embedding through {@code VectorStore.upsert}
	 * (which stores the vector as given and never embeds). Note the empty text only
	 * exists to satisfy the document's text-or-media rule; it is not content, so a
	 * document carrying this key is a reference row even though {@code isText()}
	 * reports true.
	 * @since 2.1.0
	 */
	CONTENT_REF("content_ref");

	private final String value;

	DocumentMetadata(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

// @formatter:on

	@Override
	public String toString() {
		return this.value;
	}

}
