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

package org.springframework.ai.anthropic;

import io.micrometer.common.KeyValues;

import org.springframework.ai.anthropic.AnthropicBatchObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.anthropic.AnthropicBatchObservationDocumentation.LowCardinalityKeyNames;

/**
 * Default conventions to populate observations for Anthropic Message Batches operations.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public class DefaultAnthropicBatchObservationConvention implements AnthropicBatchObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.anthropic.batch.operation";

	private static final String KEY_VALUE_NONE = "none";

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(AnthropicBatchObservationContext context) {
		return "%s %s".formatted(context.getOperation().value(), context.getProvider());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(AnthropicBatchObservationContext context) {
		KeyValues keyValues = KeyValues.empty();
		keyValues = aiOperationType(keyValues, context);
		keyValues = aiProvider(keyValues, context);
		keyValues = requestModel(keyValues, context);
		keyValues = batchStatus(keyValues, context);
		return keyValues;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(AnthropicBatchObservationContext context) {
		KeyValues keyValues = KeyValues.empty();
		keyValues = requestCount(keyValues, context);
		keyValues = requestCounts(keyValues, context);
		return keyValues;
	}

	private KeyValues aiOperationType(KeyValues keyValues, AnthropicBatchObservationContext context) {
		return keyValues.and(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), context.getOperation().value());
	}

	private KeyValues aiProvider(KeyValues keyValues, AnthropicBatchObservationContext context) {
		return keyValues.and(LowCardinalityKeyNames.AI_PROVIDER.asString(), context.getProvider());
	}

	private KeyValues requestModel(KeyValues keyValues, AnthropicBatchObservationContext context) {
		String requestModel = context.getRequestModel();
		return keyValues.and(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
				requestModel != null ? requestModel : KEY_VALUE_NONE);
	}

	private KeyValues batchStatus(KeyValues keyValues, AnthropicBatchObservationContext context) {
		AnthropicBatch batch = context.getBatch();
		return keyValues.and(LowCardinalityKeyNames.BATCH_STATUS.asString(),
				batch != null ? batch.status().getValue() : KEY_VALUE_NONE);
	}

	private KeyValues requestCount(KeyValues keyValues, AnthropicBatchObservationContext context) {
		Integer requestCount = context.getRequestCount();
		if (requestCount == null) {
			return keyValues;
		}
		return keyValues.and(HighCardinalityKeyNames.BATCH_REQUEST_COUNT.asString(), String.valueOf(requestCount));
	}

	private KeyValues requestCounts(KeyValues keyValues, AnthropicBatchObservationContext context) {
		AnthropicBatchRequestCounts counts = context.getRequestCounts();
		if (counts == null) {
			return keyValues;
		}
		return keyValues
			.and(HighCardinalityKeyNames.BATCH_PROCESSING_COUNT.asString(), String.valueOf(counts.processing()))
			.and(HighCardinalityKeyNames.BATCH_SUCCEEDED_COUNT.asString(), String.valueOf(counts.succeeded()))
			.and(HighCardinalityKeyNames.BATCH_ERRORED_COUNT.asString(), String.valueOf(counts.errored()))
			.and(HighCardinalityKeyNames.BATCH_CANCELED_COUNT.asString(), String.valueOf(counts.canceled()))
			.and(HighCardinalityKeyNames.BATCH_EXPIRED_COUNT.asString(), String.valueOf(counts.expired()));
	}

}
