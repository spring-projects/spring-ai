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

package org.springframework.ai.observation.conventions;

/**
 * Collection of attribute keys used in AI observations (spans, metrics, events). Based on
 * the OpenTelemetry Semantic Conventions for AI Systems.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/tree/main/docs/gen-ai">OTel
 * Semantic Conventions</a>.
 */
public enum AiObservationAttributes {

// @formatter:off

	// GenAI General

	/**
	 * The name of the operation being performed.
	 */
	AI_OPERATION_TYPE("gen_ai.operation.name"),
	/**
	 * The model provider as identified by the client instrumentation.
	 */
	AI_PROVIDER("gen_ai.system"),

	// GenAI Request

	/**
	 * The name of the model a request is being made to.
	 */
	REQUEST_MODEL("gen_ai.request.model"),
	/**
	 * The frequency penalty setting for the model request.
	 */
	REQUEST_FREQUENCY_PENALTY("gen_ai.request.frequency_penalty"),
	/**
	 * The maximum number of tokens the model generates for a request.
	 */
	REQUEST_MAX_TOKENS("gen_ai.request.max_tokens"),
	/**
	 * The presence penalty setting for the model request.
	 */
	REQUEST_PRESENCE_PENALTY("gen_ai.request.presence_penalty"),
	/**
	 * List of sequences that the model will use to stop generating further tokens.
	 */
	REQUEST_STOP_SEQUENCES("gen_ai.request.stop_sequences"),
	/**
	 * The temperature setting for the model request.
	 */
	REQUEST_TEMPERATURE("gen_ai.request.temperature"),
	/**
	 * List of tool definitions provided to the model in the request.
	 */
	REQUEST_TOOL_NAMES("spring.ai.model.request.tool.names"),
	/**
	 * The top_k sampling setting for the model request.
	 */
	REQUEST_TOP_K("gen_ai.request.top_k"),
	/**
	 * The top_p sampling setting for the model request.
	 */
	REQUEST_TOP_P("gen_ai.request.top_p"),

	/**
	 * The number of dimensions the resulting output embeddings have.
	 */
	REQUEST_EMBEDDING_DIMENSIONS("gen_ai.request.embedding.dimensions"),

	/**
	 * The format in which the generated image is returned.
	 */
	REQUEST_IMAGE_RESPONSE_FORMAT("gen_ai.request.image.response_format"),
	/**
	 * The size of the image to generate.
	 */
	REQUEST_IMAGE_SIZE("gen_ai.request.image.size"),
	/**
	 * The style of the image to generate.
	 */
	REQUEST_IMAGE_STYLE("gen_ai.request.image.style"),

	// GenAI Response

	/**
	 * Reasons the model stopped generating tokens, corresponding to each generation received.
	 */
	RESPONSE_FINISH_REASONS("gen_ai.response.finish_reasons"),
	/**
	 * The unique identifier for the AI response.
	 */
	RESPONSE_ID("gen_ai.response.id"),
	/**
	 * The name of the model that generated the response.
	 */
	RESPONSE_MODEL("gen_ai.response.model"),

	// GenAI Usage

	/**
	 * The number of tokens used in the model input.
	 */
	USAGE_INPUT_TOKENS("gen_ai.usage.input_tokens"),
	/**
	 * The number of tokens used in the model output.
	 */
	USAGE_OUTPUT_TOKENS("gen_ai.usage.output_tokens"),
	/**
	 * The total number of tokens used in the model exchange.
	 */
	USAGE_TOTAL_TOKENS("gen_ai.usage.total_tokens");

	private final String value;

	AiObservationAttributes(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the attribute key.
	 * @return the value of the attribute key
	 */
	public String value() {
		return this.value;
	}

// @formatter:on

}
