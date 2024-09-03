/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.embedding.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for embedding model operations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultEmbeddingModelObservationConvention implements EmbeddingModelObservationConvention {

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_MODEL_NONE = KeyValue
		.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_EMBEDDING_DIMENSION_NONE = KeyValue.of(
			EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_EMBEDDING_DIMENSIONS,
			KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_INPUT_TOKENS_NONE = KeyValue
		.of(EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS, KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_TOTAL_TOKENS_NONE = KeyValue
		.of(EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS, KeyValue.NONE_VALUE);

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(EmbeddingModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(),
					context.getRequestOptions().getModel());
		}
		return context.getOperationMetadata().operationType();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(EmbeddingModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context),
				responseModel(context));
	}

	protected KeyValue aiOperationType(EmbeddingModelObservationContext context) {
		return KeyValue.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(EmbeddingModelObservationContext context) {
		return KeyValue.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(EmbeddingModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getModel())) {
			return KeyValue.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					context.getRequestOptions().getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	protected KeyValue responseModel(EmbeddingModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& StringUtils.hasText(context.getResponse().getMetadata().getModel())) {
			return KeyValue.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL,
					context.getResponse().getMetadata().getModel());
		}
		return RESPONSE_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(EmbeddingModelObservationContext context) {
		return KeyValues.of(requestEmbeddingDimension(context), usageInputTokens(context), usageTotalTokens(context));
	}

	// Request

	protected KeyValue requestEmbeddingDimension(EmbeddingModelObservationContext context) {
		if (context.getRequestOptions().getDimensions() != null) {
			return KeyValue.of(
					EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_EMBEDDING_DIMENSIONS,
					String.valueOf(context.getRequestOptions().getDimensions()));
		}
		return REQUEST_EMBEDDING_DIMENSION_NONE;
	}

	// Response

	protected KeyValue usageInputTokens(EmbeddingModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getPromptTokens() != null) {
			return KeyValue.of(EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS,
					String.valueOf(context.getResponse().getMetadata().getUsage().getPromptTokens()));
		}
		return USAGE_INPUT_TOKENS_NONE;
	}

	protected KeyValue usageTotalTokens(EmbeddingModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getTotalTokens() != null) {
			return KeyValue.of(EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS,
					String.valueOf(context.getResponse().getMetadata().getUsage().getTotalTokens()));
		}
		return USAGE_TOTAL_TOKENS_NONE;
	}

}
