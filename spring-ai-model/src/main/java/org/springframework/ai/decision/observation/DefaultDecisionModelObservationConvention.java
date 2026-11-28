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

package org.springframework.ai.decision.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for decision model operations.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class DefaultDecisionModelObservationConvention implements DecisionModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(DecisionModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_MODEL_NONE = KeyValue
		.of(DecisionModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL, KeyValue.NONE_VALUE);

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(DecisionModelObservationContext context) {
		DecisionOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(), options.getModel());
		}
		return context.getOperationMetadata().operationType();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(DecisionModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context),
				responseModel(context));
	}

	protected KeyValue aiOperationType(DecisionModelObservationContext context) {
		return KeyValue.of(DecisionModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(DecisionModelObservationContext context) {
		return KeyValue.of(DecisionModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(DecisionModelObservationContext context) {
		DecisionOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getModel())) {
			return KeyValue.of(DecisionModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					options.getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	protected KeyValue responseModel(DecisionModelObservationContext context) {
		DecisionResponse response = context.getResponse();
		if (response != null && StringUtils.hasText(response.getMetadata().getModel())) {
			return KeyValue.of(DecisionModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL,
					response.getMetadata().getModel());
		}
		return RESPONSE_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(DecisionModelObservationContext context) {
		var keyValues = KeyValues.empty();
		// Response
		keyValues = responseId(keyValues, context);
		keyValues = usageInputTokens(keyValues, context);
		keyValues = usageOutputTokens(keyValues, context);
		keyValues = usageTotalTokens(keyValues, context);
		return keyValues;
	}

	// Response

	protected KeyValues responseId(KeyValues keyValues, DecisionModelObservationContext context) {
		DecisionResponse response = context.getResponse();
		if (response != null && StringUtils.hasText(response.getMetadata().getId())) {
			return keyValues.and(DecisionModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID.asString(),
					response.getMetadata().getId());
		}
		return keyValues;
	}

	protected KeyValues usageInputTokens(KeyValues keyValues, DecisionModelObservationContext context) {
		Usage usage = usage(context);
		if (usage != null) {
			return keyValues.and(
					DecisionModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(usage.getPromptTokens()));
		}
		return keyValues;
	}

	protected KeyValues usageOutputTokens(KeyValues keyValues, DecisionModelObservationContext context) {
		Usage usage = usage(context);
		if (usage != null) {
			return keyValues.and(
					DecisionModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(usage.getCompletionTokens()));
		}
		return keyValues;
	}

	protected KeyValues usageTotalTokens(KeyValues keyValues, DecisionModelObservationContext context) {
		Usage usage = usage(context);
		if (usage != null) {
			return keyValues.and(
					DecisionModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(usage.getTotalTokens()));
		}
		return keyValues;
	}

	private static @Nullable Usage usage(DecisionModelObservationContext context) {
		DecisionResponse response = context.getResponse();
		return response != null ? response.getMetadata().getUsage() : null;
	}

}
