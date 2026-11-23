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

package org.springframework.ai.audio.transcription.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for transcription model operations.
 *
 * @author Christian Tzolov
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class DefaultTranscriptionModelObservationConvention implements TranscriptionModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(TranscriptionModelObservationContext context) {
		AudioTranscriptionOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(), options.getModel());
		}
		return context.getOperationMetadata().operationType();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(TranscriptionModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context));
	}

	protected KeyValue aiOperationType(TranscriptionModelObservationContext context) {
		return KeyValue.of(TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(TranscriptionModelObservationContext context) {
		return KeyValue.of(TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(TranscriptionModelObservationContext context) {
		AudioTranscriptionOptions options = context.getRequest().getOptions();
		if (options != null && StringUtils.hasText(options.getModel())) {
			return KeyValue.of(TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					options.getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(TranscriptionModelObservationContext context) {
		// The portable transcription options carry the model only, and the audio file's
		// name would be a path on the caller's machine: only the streaming mode is
		// reported.
		return requestStream(KeyValues.empty(), context);
	}

	protected KeyValues requestStream(KeyValues keyValues, TranscriptionModelObservationContext context) {
		if (context.isStreaming()) {
			return keyValues.and(
					TranscriptionModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STREAM.asString(),
					String.valueOf(true));
		}
		return keyValues;
	}

}
