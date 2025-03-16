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

package org.springframework.ai.document;

import org.springframework.ai.vectorstore.VectorStore;

/**
 * Common set of metadata keys used in {@link Document}s by {@link DocumentReader}s and
 * {@link VectorStore}s.
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
	DISTANCE("distance");

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
