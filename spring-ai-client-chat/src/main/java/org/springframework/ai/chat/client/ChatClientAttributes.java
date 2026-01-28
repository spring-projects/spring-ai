/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client;

/**
 * Common attributes used in {@link ChatClient} context.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum ChatClientAttributes {

	//@formatter:off

	OUTPUT_FORMAT("spring.ai.chat.client.output.format"),

	STRUCTURED_OUTPUT_SCHEMA("spring.ai.chat.client.structured.output.schema"),

	STRUCTURED_OUTPUT_NATIVE("spring.ai.chat.client.structured.output.native");

	//@formatter:on

	private final String key;

	ChatClientAttributes(String key) {
		this.key = key;
	}

	public String getKey() {
		return this.key;
	}

}
