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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

import org.springframework.ai.observation.conventions.AiObservationAttributes;

/**
 * Documented conventions for Anthropic Message Batches observations.
 *
 * <p>
 * Neither the batch identifier nor any prompt or generated content is exposed as a tag:
 * batch ids are unbounded in cardinality and prompt content must not reach a metrics
 * backend.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public enum AnthropicBatchObservationDocumentation implements ObservationDocumentation {

	/**
	 * Observation emitted around each Anthropic Message Batches operation.
	 */
	BATCH_MODEL_OPERATION {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAnthropicBatchObservationConvention.class;
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
	 * Low-cardinality observation key names for batch operations.
	 */
	public enum LowCardinalityKeyNames implements KeyName {

		/**
		 * The batch operation being performed: {@code batch_create},
		 * {@code batch_retrieve}, {@code batch_results}, {@code batch_cancel} or
		 * {@code batch_delete}.
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
		 * The name of the model the batch entries target, or {@code none} when the
		 * entries do not share a single model.
		 */
		REQUEST_MODEL {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_MODEL.value();
			}
		},

		/**
		 * The processing status of the batch: {@code in_progress}, {@code canceling},
		 * {@code ended}, or {@code none} when the operation returns no batch.
		 */
		BATCH_STATUS {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.status";
			}
		}

	}

	/**
	 * High-cardinality observation key names for batch operations.
	 */
	public enum HighCardinalityKeyNames implements KeyName {

		/**
		 * The number of requests submitted with the batch.
		 */
		BATCH_REQUEST_COUNT {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.request.count";
			}
		},

		/**
		 * The number of requests still being processed.
		 */
		BATCH_PROCESSING_COUNT {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.counts.processing";
			}
		},

		/**
		 * The number of requests that completed successfully.
		 */
		BATCH_SUCCEEDED_COUNT {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.counts.succeeded";
			}
		},

		/**
		 * The number of requests that failed.
		 */
		BATCH_ERRORED_COUNT {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.counts.errored";
			}
		},

		/**
		 * The number of requests canceled before completion.
		 */
		BATCH_CANCELED_COUNT {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.counts.canceled";
			}
		},

		/**
		 * The number of requests that expired before completion.
		 */
		BATCH_EXPIRED_COUNT {
			@Override
			public String asString() {
				return "spring.ai.anthropic.batch.counts.expired";
			}
		}

	}

}
