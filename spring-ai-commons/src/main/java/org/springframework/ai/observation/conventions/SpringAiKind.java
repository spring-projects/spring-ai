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
 * Types of Spring AI constructs which can be observed.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum SpringAiKind {

	// @formatter:off

	// Please, keep the alphabetical sorting.
	/**
	 * Spring AI kind for advisor.
	 */
	ADVISOR("advisor"),

	/**
	 * Spring AI kind for chat client.
	 */
	CHAT_CLIENT("chat_client"),

	/**
	 * Spring AI kind for tool calling.
	 */
	TOOL_CALL("tool_call"),

	/**
	 * Spring AI kind for vector store.
	 */
	VECTOR_STORE("vector_store");

	private final String value;

	SpringAiKind(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the Spring AI kind.
	 * @return the value of the Spring AI kind
	 */
	public String value() {
		return this.value;
	}

	// @formatter:on

}
