package org.springframework.ai.micrometer.model;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * Default conventions to produce metrics and spans for LLM operations.
 *
 * @author Thomas Vitale
 */
public class DefaultModelObservationConvention implements ModelObservationConvention {

	private static final KeyValue OPERATION_TYPE_NONE = KeyValue
		.of(ModelObservation.LowCardinalityKeyNames.OPERATION_NAME, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_FREQUENCY_PENALTY_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_MAX_TOKENS_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_MAX_TOKENS, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_PRESENCE_PENALTY_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_STOP_SEQUENCES_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_STOP_SEQUENCES, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_TEMPERATURE_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_TEMPERATURE, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_TOP_K_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_TOP_K, KeyValue.NONE_VALUE);

	private static final KeyValue REQUEST_TOP_P_NONE = KeyValue
		.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_TOP_P, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_FINISH_REASON_NONE = KeyValue
		.of(ModelObservation.ResponseHighCardinalityKeyNames.RESPONSE_FINISH_REASON, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_ID_NONE = KeyValue
		.of(ModelObservation.ResponseHighCardinalityKeyNames.RESPONSE_ID, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_MODEL_NONE = KeyValue
		.of(ModelObservation.ResponseHighCardinalityKeyNames.RESPONSE_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_COMPLETION_TOKENS_NONE = KeyValue
		.of(ModelObservation.UsageHighCardinalityKeyNames.USAGE_COMPLETION_TOKENS, KeyValue.NONE_VALUE);

	private static final KeyValue USAGE_PROMPT_TOKENS_NONE = KeyValue
		.of(ModelObservation.UsageHighCardinalityKeyNames.USAGE_PROMPT_TOKENS, KeyValue.NONE_VALUE);

	private static final String DEFAULT_NAME = "gen_ai.client.operation";

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(ModelObservationContext context) {
		return "%s.%s".formatted(context.getModelRequestContext().system(),
				context.getModelRequestContext().operationName());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ModelObservationContext context) {
		return KeyValues.of(requestModel(context), genAiOperationType(context), genAiSystem(context));
	}

	protected KeyValue requestModel(ModelObservationContext context) {
		return KeyValue.of(ModelObservation.LowCardinalityKeyNames.REQUEST_MODEL,
				context.getModelRequestContext().model());
	}

	protected KeyValue genAiOperationType(ModelObservationContext context) {
		if (context.getModelRequestContext().operationName() != null) {
			return KeyValue.of(ModelObservation.LowCardinalityKeyNames.OPERATION_NAME,
					context.getModelRequestContext().operationName());
		}
		return OPERATION_TYPE_NONE;
	}

	protected KeyValue genAiSystem(ModelObservationContext context) {
		return KeyValue.of(ModelObservation.LowCardinalityKeyNames.SYSTEM, context.getModelRequestContext().system());
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ModelObservationContext context) {
		// @formatter:off
		return KeyValues.of(
                // Request
                requestFrequencyPenalty(context), requestMaxTokens(context), requestPresencePenalty(context),
                requestStopSequences(context), requestTemperature(context), requestTopK(context), requestTopP(context),
                // Response
                responseFinishReason(context), responseId(context), responseModel(context),
                usageCompletionTokens(context), usagePromptTokens(context));
		// @formatter:on
	}

	// Request

	protected KeyValue requestFrequencyPenalty(ModelObservationContext context) {
		if (context.getModelRequestContext().frequencyPenalty() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY,
					String.valueOf(context.getModelRequestContext().frequencyPenalty()));
		}
		return REQUEST_FREQUENCY_PENALTY_NONE;
	}

	protected KeyValue requestMaxTokens(ModelObservationContext context) {
		if (context.getModelRequestContext().maxTokens() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_MAX_TOKENS,
					String.valueOf(context.getModelRequestContext().maxTokens()));
		}
		return REQUEST_MAX_TOKENS_NONE;
	}

	protected KeyValue requestPresencePenalty(ModelObservationContext context) {
		if (context.getModelRequestContext().presencePenalty() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY,
					String.valueOf(context.getModelRequestContext().presencePenalty()));
		}
		return REQUEST_PRESENCE_PENALTY_NONE;
	}

	protected KeyValue requestStopSequences(ModelObservationContext context) {
		if (context.getModelRequestContext().stopSequences() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_STOP_SEQUENCES,
					String.join(",", context.getModelRequestContext().stopSequences()));
		}
		return REQUEST_STOP_SEQUENCES_NONE;
	}

	protected KeyValue requestTemperature(ModelObservationContext context) {
		if (context.getModelRequestContext().temperature() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_TEMPERATURE,
					String.valueOf(context.getModelRequestContext().temperature()));
		}
		return REQUEST_TEMPERATURE_NONE;
	}

	protected KeyValue requestTopK(ModelObservationContext context) {
		if (context.getModelRequestContext().topK() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_TOP_K,
					String.valueOf(context.getModelRequestContext().topK()));
		}
		return REQUEST_TOP_K_NONE;
	}

	protected KeyValue requestTopP(ModelObservationContext context) {
		if (context.getModelRequestContext().topP() != null) {
			return KeyValue.of(ModelObservation.RequestHighCardinalityKeyNames.REQUEST_TOP_P,
					String.valueOf(context.getModelRequestContext().topP()));
		}
		return REQUEST_TOP_P_NONE;
	}

	// Response

	protected KeyValue responseFinishReason(ModelObservationContext context) {
		if (context.getModelResponseContext() != null && context.getModelResponseContext().finishReason() != null) {
			return KeyValue.of(ModelObservation.ResponseHighCardinalityKeyNames.RESPONSE_FINISH_REASON,
					String.join(",", context.getModelResponseContext().finishReason()));
		}
		return RESPONSE_FINISH_REASON_NONE;
	}

	protected KeyValue responseId(ModelObservationContext context) {
		if (context.getModelResponseContext() != null && context.getModelResponseContext().responseId() != null) {
			return KeyValue.of(ModelObservation.ResponseHighCardinalityKeyNames.RESPONSE_ID,
					context.getModelResponseContext().responseId());
		}
		return RESPONSE_ID_NONE;
	}

	protected KeyValue responseModel(ModelObservationContext context) {
		if (context.getModelResponseContext() != null && context.getModelResponseContext().responseModel() != null) {
			return KeyValue.of(ModelObservation.ResponseHighCardinalityKeyNames.RESPONSE_MODEL,
					context.getModelResponseContext().responseModel());
		}
		return RESPONSE_MODEL_NONE;
	}

	protected KeyValue usageCompletionTokens(ModelObservationContext context) {
		if (context.getModelResponseContext() != null && context.getModelResponseContext().completionTokens() != null) {
			return KeyValue.of(ModelObservation.UsageHighCardinalityKeyNames.USAGE_COMPLETION_TOKENS,
					String.valueOf(context.getModelResponseContext().completionTokens()));
		}
		return USAGE_COMPLETION_TOKENS_NONE;
	}

	protected KeyValue usagePromptTokens(ModelObservationContext context) {
		if (context.getModelResponseContext() != null && context.getModelResponseContext().promptTokens() != null) {
			return KeyValue.of(ModelObservation.UsageHighCardinalityKeyNames.USAGE_PROMPT_TOKENS,
					String.valueOf(context.getModelResponseContext().promptTokens()));
		}
		return USAGE_PROMPT_TOKENS_NONE;
	}

}
