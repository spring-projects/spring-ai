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
 * Collection of systems providing AI functionality. Inspired by the OpenTelemetry
 * Semantic Conventions for Generative AI.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai">OpenTelemetry
 * Semantic Conventions for Generative AI</a>.
 */
public enum AiProvider {

	// @formatter:off

	/**
	 * AI system provided by Anthropic.
	 */
	ANTHROPIC("anthropic"),

	/**
	 * AI system provided by Bedrock Converse.
	 */
	BEDROCK_CONVERSE("bedrock_converse"),

	/**
	 * AI system provided by DeepSeek.
	 */
	DEEPSEEK("deepseek"),

	/**
	 * AI system provided by Google Gen AI.
	 */
	GOOGLE_GENAI_AI("google_genai"),

	/**
	 * AI system provided by Minimax.
	 */
	MINIMAX("minimax"),

	/**
	 * AI system provided by Mistral.
	 */
	MISTRAL_AI("mistral_ai"),

	/**
	 * AI system provided by Ollama.
	 */
	OLLAMA("ollama"),

	/**
	 * AI system provided by ONNX.
	 */
	ONNX("onnx"),

	/**
	 * AI system provided by OpenAI.
	 */
	OPENAI("openai"),

	/**
	 * AI system provided by Spring AI.
	 */
	SPRING_AI("spring_ai"),

	/**
	 * AI system provided by Vertex AI.
	 */
	VERTEX_AI("vertex_ai");

	private final String value;

	AiProvider(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the provider.
	 * @return the value of the provider
	 */
	public String value() {
		return this.value;
	}

	// @formatter:on

}
