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

package org.springframework.ai.image.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

/**
 * Documented conventions for image model observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum ImageModelObservationDocumentation implements ObservationDocumentation {

	IMAGE_MODEL_OPERATION {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultImageModelObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return HighCardinalityKeyNames.values();
		}

	};

	/**
	 * Low-cardinality observation key names for image model operations.
	 */
	public enum LowCardinalityKeyNames implements KeyName {

		/**
		 * The name of the operation being performed.
		 */
		AI_OPERATION_TYPE {
			@Override
			public String asString() {
				return AiObservationAttributes.AI_OPERATION_TYPE.value();
			}
		},

		/**
		 * The model provider as identified by the client instrumentation.
		 */
		AI_PROVIDER {
			@Override
			public String asString() {
				return AiObservationAttributes.AI_PROVIDER.value();
			}
		},

		/**
		 * The name of the model a request is being made to.
		 */
		REQUEST_MODEL {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_MODEL.value();
			}
		}

	}

	/**
	 * High-cardinality observation key names for image model operations.
	 */
	public enum HighCardinalityKeyNames implements KeyName {

		// Request

		/**
		 * The format in which the generated image is returned.
		 */
		REQUEST_IMAGE_RESPONSE_FORMAT {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_IMAGE_RESPONSE_FORMAT.value();
			}
		},

		/**
		 * The size of the image to generate.
		 */
		REQUEST_IMAGE_SIZE {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_IMAGE_SIZE.value();
			}
		},

		/**
		 * The style of the image to generate.
		 */
		REQUEST_IMAGE_STYLE {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_IMAGE_STYLE.value();
			}
		},

		// Response

		/**
		 * The unique identifier for the AI response.
		 */
		RESPONSE_ID {
			@Override
			public String asString() {
				return AiObservationAttributes.RESPONSE_ID.value();
			}
		},

		/**
		 * The name of the model that generated the response.
		 */
		RESPONSE_MODEL {
			@Override
			public String asString() {
				return AiObservationAttributes.RESPONSE_MODEL.value();
			}
		},

		// Usage

		/**
		 * The number of tokens used in the model input (prompt).
		 */
		USAGE_INPUT_TOKENS {
			@Override
			public String asString() {
				return AiObservationAttributes.USAGE_INPUT_TOKENS.value();
			}
		},

		/**
		 * The number of tokens used in the model output (generation).
		 */
		USAGE_OUTPUT_TOKENS {
			@Override
			public String asString() {
				return AiObservationAttributes.USAGE_OUTPUT_TOKENS.value();
			}
		},

		/**
		 * The total number of tokens used in the model exchange.
		 */
		USAGE_TOTAL_TOKENS {
			@Override
			public String asString() {
				return AiObservationAttributes.USAGE_TOTAL_TOKENS.value();
			}
		}

	}

}
