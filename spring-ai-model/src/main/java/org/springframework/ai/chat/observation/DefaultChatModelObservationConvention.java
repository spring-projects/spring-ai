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

package org.springframework.ai.chat.observation;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for chat model operations.
 *
 * @author Thomas Vitale
 * @author Soby Chacko
 * @since 1.0.0
 */
public class DefaultChatModelObservationConvention implements ChatModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	private static final KeyValue RESPONSE_MODEL_NONE = KeyValue
		.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL, KeyValue.NONE_VALUE);

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (StringUtils.hasText(options.getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(), options.getModel());
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
		ChatOptions options = context.getRequest().getOptions();
		if (StringUtils.hasText(options.getModel())) {
			return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					options.getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	protected KeyValue responseModel(ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& StringUtils.hasText(context.getResponse().getMetadata().getModel())) {
			return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL,
					context.getResponse().getMetadata().getModel());
		}
		return RESPONSE_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ChatModelObservationContext context) {
		var keyValues = KeyValues.empty();
		// Request
		keyValues = requestFrequencyPenalty(keyValues, context);
		keyValues = requestMaxTokens(keyValues, context);
		keyValues = requestPresencePenalty(keyValues, context);
		keyValues = requestStopSequences(keyValues, context);
		keyValues = requestTemperature(keyValues, context);
		keyValues = requestTools(keyValues, context);
		keyValues = requestTopK(keyValues, context);
		keyValues = requestTopP(keyValues, context);
		// Response
		keyValues = responseFinishReasons(keyValues, context);
		keyValues = responseId(keyValues, context);
		keyValues = usageInputTokens(keyValues, context);
		keyValues = usageOutputTokens(keyValues, context);
		keyValues = usageTotalTokens(keyValues, context);
		return keyValues;
	}

	// Request

	protected KeyValues requestFrequencyPenalty(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (options.getFrequencyPenalty() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(),
					String.valueOf(options.getFrequencyPenalty()));
		}
		return keyValues;
	}

	protected KeyValues requestMaxTokens(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (options.getMaxTokens() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(),
					String.valueOf(options.getMaxTokens()));
		}
		return keyValues;
	}

	protected KeyValues requestPresencePenalty(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (options.getPresencePenalty() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(),
					String.valueOf(options.getPresencePenalty()));
		}
		return keyValues;
	}

	protected KeyValues requestStopSequences(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (!CollectionUtils.isEmpty(options.getStopSequences())) {
			StringJoiner stopSequencesJoiner = new StringJoiner(", ", "[", "]");
			options.getStopSequences().forEach(value -> stopSequencesJoiner.add("\"" + value + "\""));
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					stopSequencesJoiner.toString());
		}
		return keyValues;
	}

	protected KeyValues requestTemperature(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (options.getTemperature() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(),
					String.valueOf(options.getTemperature()));
		}
		return keyValues;
	}

	protected KeyValues requestTools(KeyValues keyValues, ChatModelObservationContext context) {
		if (!(context.getRequest().getOptions() instanceof ToolCallingChatOptions options)) {
			return keyValues;
		}

		Set<String> toolNames = new HashSet<>(options.getToolNames());
		toolNames.addAll(options.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList());

		if (!CollectionUtils.isEmpty(toolNames)) {
			StringJoiner toolNamesJoiner = new StringJoiner(", ", "[", "]");
			toolNames.forEach(value -> toolNamesJoiner.add("\"" + value + "\""));
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_NAMES.asString(),
					toolNamesJoiner.toString());
		}
		return keyValues;
	}

	protected KeyValues requestTopK(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (options.getTopK() != null) {
			return keyValues.and(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K.asString(),
					String.valueOf(options.getTopK()));
		}
		return keyValues;
	}

	protected KeyValues requestTopP(KeyValues keyValues, ChatModelObservationContext context) {
		ChatOptions options = context.getRequest().getOptions();
		if (options.getTopP() != null) {
			return keyValues.and(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P.asString(),
					String.valueOf(options.getTopP()));
		}
		return keyValues;
	}

	// Response

	protected KeyValues responseFinishReasons(KeyValues keyValues, ChatModelObservationContext context) {
		if (context.getResponse() != null && !CollectionUtils.isEmpty(context.getResponse().getResults())) {
			var finishReasons = context.getResponse()
				.getResults()
				.stream()
				.filter(generation -> StringUtils.hasText(generation.getMetadata().getFinishReason()))
				.map(generation -> generation.getMetadata().getFinishReason())
				.toList();
			if (CollectionUtils.isEmpty(finishReasons)) {
				return keyValues;
			}
			StringJoiner finishReasonsJoiner = new StringJoiner(", ", "[", "]");
			finishReasons.forEach(finishReason -> finishReasonsJoiner.add("\"" + finishReason + "\""));
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(),
					finishReasonsJoiner.toString());
		}
		return keyValues;
	}

	protected KeyValues responseId(KeyValues keyValues, ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& StringUtils.hasText(context.getResponse().getMetadata().getId())) {
			return keyValues.and(ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID.asString(),
					context.getResponse().getMetadata().getId());
		}
		return keyValues;
	}

	protected KeyValues usageInputTokens(KeyValues keyValues, ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getPromptTokens() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(context.getResponse().getMetadata().getUsage().getPromptTokens()));
		}
		return keyValues;
	}

	protected KeyValues usageOutputTokens(KeyValues keyValues, ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getCompletionTokens() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(context.getResponse().getMetadata().getUsage().getCompletionTokens()));
		}
		return keyValues;
	}

	protected KeyValues usageTotalTokens(KeyValues keyValues, ChatModelObservationContext context) {
		if (context.getResponse() != null && context.getResponse().getMetadata() != null
				&& context.getResponse().getMetadata().getUsage() != null
				&& context.getResponse().getMetadata().getUsage().getTotalTokens() != null) {
			return keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(context.getResponse().getMetadata().getUsage().getTotalTokens()));
		}
		return keyValues;
	}

}
