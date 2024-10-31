/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for chat model operations.
 *
 * @author Thomas Vitale
 * @author John Blum
 * @since 1.0.0
 */
public class DefaultChatModelObservationConvention implements ChatModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	protected static final String CONTEXTUAL_NAME_TEMPLATE = "%s %s";

	// @formatter:off
	private static final ChatModelObservationDocumentation.LowCardinalityKeyNames AI_OPERATION_TYPE_KEY_NAME =
		ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE;

	private static final ChatModelObservationDocumentation.LowCardinalityKeyNames AI_PROVIDER_KEY_NAME =
		ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames REQUEST_FREQUENCY_PENALTY_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames REQUEST_MAX_TOKENS_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS;

	private static final ChatModelObservationDocumentation.LowCardinalityKeyNames REQUEST_MODEL_KEY_NAME =
		ChatModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames REQUEST_PRESENCE_PENALTY_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames REQUEST_STOP_SEQUENCES_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames REQUEST_TEMPERATURE_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE;

	private static final ChatModelObservationDocumentation.LowCardinalityKeyNames RESPONSE_MODEL_KEY_NAME =
		ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames TOP_K_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames TOP_P_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames USAGE_TOTAL_TOKENS_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS;

	private static final ChatModelObservationDocumentation.HighCardinalityKeyNames USAGE_OUTPUT_TOKENS_KEY_NAME =
		ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS;

	private static final KeyValue REQUEST_MODEL_NONE = keyValueOf(REQUEST_MODEL_KEY_NAME);

	private static final KeyValue RESPONSE_MODEL_NONE = keyValueOf(RESPONSE_MODEL_KEY_NAME);
	// @formatter:on

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(ChatModelObservationContext context) {

		return resolveRequestModelName(context).map(modelName -> getContextualName(context, modelName))
			.orElseGet(() -> context.getOperationMetadata().operationType());
	}

	private String getContextualName(ChatModelObservationContext context, String modelName) {
		return CONTEXTUAL_NAME_TEMPLATE.formatted(context.getOperationMetadata().operationType(), modelName);
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ChatModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context),
				responseModel(context));
	}

	protected KeyValue aiOperationType(ChatModelObservationContext context) {
		return KeyValue.of(AI_OPERATION_TYPE_KEY_NAME, context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(ChatModelObservationContext context) {
		return KeyValue.of(AI_PROVIDER_KEY_NAME, context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(ChatModelObservationContext context) {

		return resolveRequestModelName(context).map(modelName -> KeyValue.of(REQUEST_MODEL_KEY_NAME, modelName))
			.orElse(REQUEST_MODEL_NONE);
	}

	protected KeyValue responseModel(ChatModelObservationContext context) {

		return resolveResponseModelName(context).map(modelName -> KeyValue.of(RESPONSE_MODEL_KEY_NAME, modelName))
			.orElse(RESPONSE_MODEL_NONE);
	}

	private Optional<String> resolveRequestModelName(@Nullable ChatModelObservationContext context) {

		return Optional.ofNullable(context)
			.map(ChatModelObservationContext::getRequestOptions)
			.map(ChatOptions::getModel)
			.filter(StringUtils::hasText);
	}

	private Optional<String> resolveResponseModelName(@Nullable ChatModelObservationContext context) {

		return Optional.ofNullable(context)
			.map(ChatModelObservationContext::getResponse)
			.map(ChatResponse::getMetadata)
			.map(ChatResponseMetadata::getModel)
			.filter(StringUtils::hasText);
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

		Double frequencyPenalty = context.getRequestOptions().getFrequencyPenalty();

		return frequencyPenalty != null
				? keyValues.and(keyValueOf(REQUEST_FREQUENCY_PENALTY_KEY_NAME, frequencyPenalty)) : keyValues;
	}

	protected KeyValues requestMaxTokens(KeyValues keyValues, ChatModelObservationContext context) {

		Integer maxTokens = context.getRequestOptions().getMaxTokens();

		return maxTokens != null ? keyValues.and(keyValueOf(REQUEST_MAX_TOKENS_KEY_NAME, maxTokens)) : keyValues;
	}

	protected KeyValues requestPresencePenalty(KeyValues keyValues, ChatModelObservationContext context) {

		Double presencePenalty = context.getRequestOptions().getPresencePenalty();

		return presencePenalty != null ? keyValues.and(keyValueOf(REQUEST_PRESENCE_PENALTY_KEY_NAME, presencePenalty))
				: keyValues;
	}

	protected KeyValues requestStopSequences(KeyValues keyValues, ChatModelObservationContext context) {

		List<String> stopSequences = context.getRequestOptions().getStopSequences();

		if (!CollectionUtils.isEmpty(stopSequences)) {
			StringJoiner stopSequencesJoiner = new StringJoiner(", ", "[", "]");
			stopSequences.forEach(value -> stopSequencesJoiner.add("\"" + value + "\""));
			keyValues = keyValues.and(keyValueOf(REQUEST_STOP_SEQUENCES_KEY_NAME, stopSequencesJoiner));
		}

		return keyValues;
	}

	protected KeyValues requestTemperature(KeyValues keyValues, ChatModelObservationContext context) {

		Double temperature = context.getRequestOptions().getTemperature();

		return temperature != null ? keyValues.and(keyValueOf(REQUEST_TEMPERATURE_KEY_NAME, temperature)) : keyValues;
	}

	protected KeyValues requestTopK(KeyValues keyValues, ChatModelObservationContext context) {

		Integer topK = context.getRequestOptions().getTopK();

		return topK != null ? keyValues.and(keyValueOf(TOP_K_KEY_NAME, topK)) : keyValues;
	}

	protected KeyValues requestTopP(KeyValues keyValues, ChatModelObservationContext context) {

		Double topP = context.getRequestOptions().getTopP();

		return topP != null ? keyValues.and(keyValueOf(TOP_P_KEY_NAME, topP)) : keyValues;
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

		return resolveMetadata(context).map(ChatResponseMetadata::getId)
			.filter(StringUtils::hasText)
			.map(id -> keyValues.and(ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID.asString(),
					id))
			.orElse(keyValues);
	}

	protected KeyValues usageInputTokens(KeyValues keyValues, ChatModelObservationContext context) {

		return resolveUsage(context).map(Usage::getPromptTokens)
			.map(promptTokens -> keyValues.and(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(promptTokens)))
			.orElse(keyValues);
	}

	protected KeyValues usageOutputTokens(KeyValues keyValues, ChatModelObservationContext context) {

		return resolveUsage(context).map(Usage::getGenerationTokens)
			.map(generatedTokens -> keyValues.and(keyValueOf(USAGE_OUTPUT_TOKENS_KEY_NAME, generatedTokens)))
			.orElse(keyValues);
	}

	protected KeyValues usageTotalTokens(KeyValues keyValues, ChatModelObservationContext context) {

		return resolveUsage(context).map(Usage::getTotalTokens)
			.map(totalTokens -> keyValues.and(keyValueOf(USAGE_TOTAL_TOKENS_KEY_NAME, totalTokens)))
			.orElse(keyValues);
	}

	private static KeyValue keyValueOf(KeyName keyName) {
		return keyValueOf(keyName, KeyValue.NONE_VALUE);
	}

	private static KeyValue keyValueOf(KeyName keyName, Object value) {
		return KeyValue.of(keyName.asString(), String.valueOf(value));
	}

	private Optional<ChatResponseMetadata> resolveMetadata(@Nullable ChatModelObservationContext context) {

		return Optional.ofNullable(context)
			.map(ChatModelObservationContext::getResponse)
			.map(ChatResponse::getMetadata);
	}

	private Optional<Usage> resolveUsage(@Nullable ChatModelObservationContext context) {
		return resolveMetadata(context).map(ChatResponseMetadata::getUsage);
	}

}
