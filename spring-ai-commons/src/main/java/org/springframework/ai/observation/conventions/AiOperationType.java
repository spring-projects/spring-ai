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

package org.springframework.ai.observation.conventions;

/**
 * Types of operations performed by AI systems. Inspired by the OpenTelemetry Semantic
 * Conventions for Generative AI.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai">OpenTelemetry
 * Semantic Conventions for Generative AI</a>.
 */
public enum AiOperationType {

	// @formatter:off

	/**
	 * AI operation type for chat completion.
	 */
	CHAT("chat"),

	/**
	 * AI operation type for embedding.
	 */
	EMBEDDING("embedding"),

	/**
	 * AI operation type for tool execution.
	 */
	EXECUTE_TOOL("execute_tool"),

	/**
	 * AI operation type for framework.
	 */
	FRAMEWORK("framework"),

	/**
	 * AI operation type for image.
	 */
	IMAGE("image"),

	/**
	 * AI operation type for text completion.
	 */
	TEXT_COMPLETION("text_completion");

	private final String value;

	AiOperationType(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the operation type.
	 * @return the value of the operation type
	 */
	public String value() {
		return this.value;
	}

	// @formatter:on

}
