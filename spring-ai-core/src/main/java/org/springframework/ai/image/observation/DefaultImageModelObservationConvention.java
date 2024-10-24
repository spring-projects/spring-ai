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

package org.springframework.ai.image.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for image model operations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultImageModelObservationConvention implements ImageModelObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.client.operation";

	private static final KeyValue REQUEST_MODEL_NONE = KeyValue
		.of(ImageModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL, KeyValue.NONE_VALUE);

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(ImageModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getModel())) {
			return "%s %s".formatted(context.getOperationMetadata().operationType(),
					context.getRequestOptions().getModel());
		}
		return context.getOperationMetadata().operationType();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ImageModelObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), requestModel(context));
	}

	protected KeyValue aiOperationType(ImageModelObservationContext context) {
		return KeyValue.of(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(ImageModelObservationContext context) {
		return KeyValue.of(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue requestModel(ImageModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getModel())) {
			return KeyValue.of(ImageModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL,
					context.getRequestOptions().getModel());
		}
		return REQUEST_MODEL_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ImageModelObservationContext context) {
		var keyValues = KeyValues.empty();
		// Request
		keyValues = requestImageFormat(keyValues, context);
		keyValues = requestImageSize(keyValues, context);
		keyValues = requestImageStyle(keyValues, context);
		return keyValues;
	}

	// Request

	protected KeyValues requestImageFormat(KeyValues keyValues, ImageModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getResponseFormat())) {
			return keyValues.and(
					ImageModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_IMAGE_RESPONSE_FORMAT.asString(),
					context.getRequestOptions().getResponseFormat());
		}
		return keyValues;
	}

	protected KeyValues requestImageSize(KeyValues keyValues, ImageModelObservationContext context) {
		if (context.getRequestOptions().getWidth() != null && context.getRequestOptions().getHeight() != null) {
			return keyValues.and(
					ImageModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(),
					"%sx%s".formatted(context.getRequestOptions().getWidth(), context.getRequestOptions().getHeight()));
		}
		return keyValues;
	}

	protected KeyValues requestImageStyle(KeyValues keyValues, ImageModelObservationContext context) {
		if (StringUtils.hasText(context.getRequestOptions().getStyle())) {
			return keyValues.and(
					ImageModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_IMAGE_STYLE.asString(),
					context.getRequestOptions().getStyle());
		}
		return keyValues;
	}

}
