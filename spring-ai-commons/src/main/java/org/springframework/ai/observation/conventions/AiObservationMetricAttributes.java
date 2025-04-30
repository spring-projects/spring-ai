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
 * Collection of metric attributes used in AI observations. Based on the OpenTelemetry
 * Semantic Conventions for AI Systems.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/tree/main/docs/gen-ai">OTel
 * Semantic Conventions</a>.
 */
public enum AiObservationMetricAttributes {

// @formatter:off

	/**
	 * The type of token being counted (input, output, total).
	 */
	TOKEN_TYPE("gen_ai.token.type");

	private final String value;

	AiObservationMetricAttributes(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the metric attribute.
	 * @return the value of the metric attribute
	 */
	public String value() {
		return this.value;
	}

// @formatter:on

}
