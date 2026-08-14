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

package org.springframework.ai.google.genai.common;

/**
 * The service tier to use for the request.
 *
 * @author Sébastien Deleuze
 * @since 2.0.0
 */
public enum GoogleGenAiServiceTier {

	/**
	 * The standard service tier.
	 */
	STANDARD("standard"),

	/**
	 * The priority service tier.
	 */
	PRIORITY("priority"),

	/**
	 * The flex service tier.
	 */
	FLEX("flex");

	private final String value;

	GoogleGenAiServiceTier(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
