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

package org.springframework.ai.observation.conventions;

/**
 * Types of similarity metrics used in vector store operations. Based on the OpenTelemetry
 * Semantic Conventions for Vector Databases.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/tree/main/docs/db">DB Semantic
 * Conventions</a>.
 */
public enum VectorStoreSimilarityMetric {

	// @formatter:off

	/**
	 *  The cosine metric.
	 */
	COSINE("cosine"),

	/**
	 * The dot product metric.
	 */
	DOT("dot"),

	/**
	 * The euclidean distance metric.
	 */
	EUCLIDEAN("euclidean"),

	/**
	 * The manhattan distance metric.
	 */
	MANHATTAN("manhattan");

	private final String value;

	VectorStoreSimilarityMetric(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

	// @formatter:on

}
