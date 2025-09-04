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

package org.springframework.ai.embedding.observation;

import java.util.Optional;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for embedding model operations.
 *
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Mengqi Xu
 * @since 1.0.0
 */
public class DefaultEmbeddingModelObservationConvention implements EmbeddingModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_MODEL_NONE = KeyValue
		.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL, KeyValue.NONE_VALUE);

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(EmbeddingModelObservationContext context) {
		return Optional.ofNullable(context.getRequest().getOptions())
			.map(EmbeddingOptions::getModel)
			.filter(StringUtils::hasText)
			.map(model -> "%s %s".formatted(context.getOperationMetadata().operationType(), model))
			.orElseGet(() -> context.getOperationMetadata().operationType());
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
		return Optional.ofNullable(context.getRequest().getOptions())
			.map(EmbeddingOptions::getModel)
			.filter(StringUtils::hasText)
			.map(model -> KeyValue.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					model))
			.orElse(REQUEST_MODEL_NONE);
	}

	protected KeyValue responseModel(EmbeddingModelObservationContext context) {
		return Optional.ofNullable(context.getResponse())
			.map(EmbeddingResponse::getMetadata)
			.map(EmbeddingResponseMetadata::getModel)
			.filter(StringUtils::hasText)
			.map(model -> KeyValue.of(EmbeddingModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL,
					model))
			.orElse(RESPONSE_MODEL_NONE);
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(EmbeddingModelObservationContext context) {
		var keyValues = KeyValues.empty();
		// Request
		keyValues = requestEmbeddingDimension(keyValues, context);
		// Response
		keyValues = usageInputTokens(keyValues, context);
		keyValues = usageTotalTokens(keyValues, context);
		return keyValues;
	}

	// Request

	protected KeyValues requestEmbeddingDimension(KeyValues keyValues, EmbeddingModelObservationContext context) {
		return Optional.ofNullable(context.getRequest().getOptions())
			.map(EmbeddingOptions::getDimensions)
			.map(dimensions -> keyValues
				.and(EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_EMBEDDING_DIMENSIONS
					.asString(), String.valueOf(dimensions)))
			.orElse(keyValues);
	}

	// Response

	protected KeyValues usageInputTokens(KeyValues keyValues, EmbeddingModelObservationContext context) {
		return Optional.ofNullable(context.getResponse())
			.map(EmbeddingResponse::getMetadata)
			.map(EmbeddingResponseMetadata::getUsage)
			.map(Usage::getPromptTokens)
			.map(promptTokens -> keyValues.and(
					EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(promptTokens)))
			.orElse(keyValues);
	}

	protected KeyValues usageTotalTokens(KeyValues keyValues, EmbeddingModelObservationContext context) {
		return Optional.ofNullable(context.getResponse())
			.map(EmbeddingResponse::getMetadata)
			.map(EmbeddingResponseMetadata::getUsage)
			.map(Usage::getTotalTokens)
			.map(totalTokens -> keyValues.and(
					EmbeddingModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(totalTokens)))
			.orElse(keyValues);
	}

}
