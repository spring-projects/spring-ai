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

package org.springframework.ai.audio.tts.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for text-to-speech model operations.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class DefaultTextToSpeechModelObservationConvention implements TextToSpeechModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(TextToSpeechModelObservationContext context) {
		TextToSpeechOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(), options.getModel());
		}
		return context.getOperationMetadata().operationType();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(TextToSpeechModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context));
	}

	protected KeyValue aiOperationType(TextToSpeechModelObservationContext context) {
		return KeyValue.of(TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(TextToSpeechModelObservationContext context) {
		return KeyValue.of(TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(TextToSpeechModelObservationContext context) {
		TextToSpeechOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getModel())) {
			return KeyValue.of(TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					options.getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(TextToSpeechModelObservationContext context) {
		var keyValues = KeyValues.empty();
		// Request
		keyValues = requestTtsVoice(keyValues, context);
		keyValues = requestTtsFormat(keyValues, context);
		keyValues = requestTtsSpeed(keyValues, context);
		return keyValues;
	}

	// Request

	protected KeyValues requestTtsVoice(KeyValues keyValues, TextToSpeechModelObservationContext context) {
		TextToSpeechOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getVoice())) {
			return keyValues.and(
					TextToSpeechModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TTS_VOICE.asString(),
					options.getVoice());
		}
		return keyValues;
	}

	protected KeyValues requestTtsFormat(KeyValues keyValues, TextToSpeechModelObservationContext context) {
		TextToSpeechOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getFormat())) {
			return keyValues.and(
					TextToSpeechModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TTS_FORMAT.asString(),
					options.getFormat());
		}
		return keyValues;
	}

	protected KeyValues requestTtsSpeed(KeyValues keyValues, TextToSpeechModelObservationContext context) {
		TextToSpeechOptions options = context.getRequest().getOptions();
		if (options != null && options.getSpeed() != null) {
			return keyValues.and(
					TextToSpeechModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TTS_SPEED.asString(),
					String.valueOf(options.getSpeed()));
		}
		return keyValues;
	}

}
