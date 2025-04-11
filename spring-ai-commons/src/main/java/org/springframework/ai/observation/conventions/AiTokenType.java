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
 * Types of tokens produced and consumed in an AI operation. Based on the OpenTelemetry
 * Semantic Conventions for AI Systems.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/tree/main/docs/gen-ai">OTel
 * Semantic Conventions</a>.
 */
public enum AiTokenType {

// @formatter:off

	/**
	 * Input token.
	 */
	INPUT("input"),
	/**
	 * Output token.
	 */
	OUTPUT("output"),
	/**
	 * Total token.
	 */
	TOTAL("total");

	private final String value;

	AiTokenType(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the token type.
	 * @return the value of the token type
	 */
	public String value() {
		return this.value;
	}

// @formatter:on

}
