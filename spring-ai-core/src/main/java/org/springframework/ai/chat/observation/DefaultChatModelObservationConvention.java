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
package org.springframework.ai.chat.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.StringJoiner;

/**
 * Default conventions to populate observations for chat model operations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultChatModelObservationConvention implements ChatModelObservationConvention {

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_MODEL_NONE = KeyValue
		.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_FREQUENCY_PENALTY_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_MAX_TOKENS_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_PRESENCE_PENALTY_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_STOP_SEQUENCES_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_TEMPERATURE_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_TOP_K_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_TOP_P_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_FINISH_REASONS_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_FINISH_REASONS, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_ID_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID, KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_INPUT_TOKENS_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS, KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_OUTPUT_TOKENS_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS, KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_TOTAL_TOKENS_NONE = KeyValue
		.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS, KeyValue.NONE_VALUE);

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(ChatModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(),
					context.getRequestOptions().getModel());
		}
		return context.getOperationMetadata().operationType();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ChatModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context),
				responseModel(context));
	}

	protected KeyValue aiOperationType(ChatModelObservationContext context) {
		return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(ChatModelObservationContext context) {
		return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(ChatModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getModel())) {
			return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					context.getRequestOptions().getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	protected KeyValue responseModel(ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getModel() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL,
					context.getResponse().getMetadata().getModel());
		}
		return RESPONSE_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ChatModelObservationContext context) {
		return KeyValues.of(requestFrequencyPenalty(context), requestMaxTokens(context),
				requestPresencePenalty(context), requestStopSequences(context), requestTemperature(context),
				requestTopK(context), requestTopP(context), responseFinishReasons(context), responseId(context),
				usageInputTokens(context), usageOutputTokens(context), usageTotalTokens(context));
	}

	// Request

	protected KeyValue requestFrequencyPenalty(ChatModelObservationContext context) {
		if (context.getRequestOptions().getFrequencyPenalty() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY,
					String.valueOf(context.getRequestOptions().getFrequencyPenalty()));
		}
		return REQUEST_FREQUENCY_PENALTY_NONE;
	}

	protected KeyValue requestMaxTokens(ChatModelObservationContext context) {
		if (context.getRequestOptions().getMaxTokens() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS,
					String.valueOf(context.getRequestOptions().getMaxTokens()));
		}
		return REQUEST_MAX_TOKENS_NONE;
	}

	protected KeyValue requestPresencePenalty(ChatModelObservationContext context) {
		if (context.getRequestOptions().getPresencePenalty() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY,
					String.valueOf(context.getRequestOptions().getPresencePenalty()));
		}
		return REQUEST_PRESENCE_PENALTY_NONE;
	}

	protected KeyValue requestStopSequences(ChatModelObservationContext context) {
		if (context.getRequestOptions().getStopSequences() != null) {
			StringJoiner stopSequencesJoiner = new StringJoiner(", ", "[", "]");
			context.getRequestOptions()
				.getStopSequences()
				.forEach(value -> stopSequencesJoiner.add("\"" + value + "\""));
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES,
					stopSequencesJoiner.toString());
		}
		return REQUEST_STOP_SEQUENCES_NONE;
	}

	protected KeyValue requestTemperature(ChatModelObservationContext context) {
		if (context.getRequestOptions().getTemperature() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE,
					String.valueOf(context.getRequestOptions().getTemperature()));
		}
		return REQUEST_TEMPERATURE_NONE;
	}

	protected KeyValue requestTopK(ChatModelObservationContext context) {
		if (context.getRequestOptions().getTopK() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K,
					String.valueOf(context.getRequestOptions().getTopK()));
		}
		return REQUEST_TOP_K_NONE;
	}

	protected KeyValue requestTopP(ChatModelObservationContext context) {
		if (context.getRequestOptions().getTopP() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P,
					String.valueOf(context.getRequestOptions().getTopP()));
		}
		return REQUEST_TOP_P_NONE;
	}

	// Response

	protected KeyValue responseFinishReasons(ChatModelObservationContext context) {
		if (context.getResponse() != null && !CollectionUtils.isEmpty(context.getResponse().getResults())) {
			StringJoiner finishReasonsJoiner = new StringJoiner(", ", "[", "]");
			context.getResponse()
				.getResults()
				.forEach(generation -> finishReasonsJoiner
					.add("\"" + generation.getMetadata().getFinishReason() + "\""));
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_FINISH_REASONS,
					finishReasonsJoiner.toString());
		}
		return RESPONSE_FINISH_REASONS_NONE;
	}

	protected KeyValue responseId(ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getId() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID,
					context.getResponse().getMetadata().getId());
		}
		return RESPONSE_ID_NONE;
	}

	protected KeyValue usageInputTokens(ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getPromptTokens() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS,
					String.valueOf(context.getResponse().getMetadata().getUsage().getPromptTokens()));
		}
		return USAGE_INPUT_TOKENS_NONE;
	}

	protected KeyValue usageOutputTokens(ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getGenerationTokens() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS,
					String.valueOf(context.getResponse().getMetadata().getUsage().getGenerationTokens()));
		}
		return USAGE_OUTPUT_TOKENS_NONE;
	}

	protected KeyValue usageTotalTokens(ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getTotalTokens() != null) {
			return KeyValue.of(ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS,
					String.valueOf(context.getResponse().getMetadata().getUsage().getTotalTokens()));
		}
		return USAGE_TOTAL_TOKENS_NONE;
	}

}
