package org.springframework.ai.micrometer.model;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented conventions for model observations.
 *
 * @author Thomas Vitale
 */
public enum ModelObservation implements ObservationDocumentation {

	MODEL_CALL {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultModelObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return KeyName.merge(RequestHighCardinalityKeyNames.values(), ResponseHighCardinalityKeyNames.values(),
					UsageHighCardinalityKeyNames.values(), ContentHighCardinalityKeyNames.values());
		}
	};

	/**
	 * Low-cardinality observation key names for model operations.
	 */
	public enum LowCardinalityKeyNames implements KeyName {

		/**
		 * Name of the AI system the client is interacting with.
		 */
		SYSTEM {
			@Override
			public String asString() {
				return "gen_ai.system";
			}
		},

		/**
		 * Type of AI operation performed.
		 */
		OPERATION_NAME {
			@Override
			public String asString() {
				return "gen_ai.operation.name";
			}
		},

		/**
		 * Name of the model the request is sent to.
		 */
		REQUEST_MODEL {
			@Override
			public String asString() {
				return "gen_ai.request.model";
			}
		}

	}

	/**
	 * Model request, high-cardinality observation key names for model operations.
	 */
	public enum RequestHighCardinalityKeyNames implements KeyName {

		/**
		 * Frequency penalty setting for the model request.
		 */
		REQUEST_FREQUENCY_PENALTY {
			@Override
			public String asString() {
				return "gen_ai.request.frequency_penalty";
			}
		},

		/**
		 * Maximum number of tokens the model can generate for a request.
		 */
		REQUEST_MAX_TOKENS {
			@Override
			public String asString() {
				return "gen_ai.request.max_tokens";
			}
		},

		/**
		 * Presence penalty setting for the model request.
		 */
		REQUEST_PRESENCE_PENALTY {
			@Override
			public String asString() {
				return "gen_ai.request.presence_penalty";
			}
		},

		/**
		 * List of sequences that the model will use to stop generating further tokens.
		 */
		REQUEST_STOP_SEQUENCES {
			@Override
			public String asString() {
				return "gen_ai.request.stop_sequences";
			}
		},

		/**
		 * Temperature setting for the model request.
		 */
		REQUEST_TEMPERATURE {
			@Override
			public String asString() {
				return "gen_ai.request.temperature";
			}
		},

		/**
		 * Top-K sampling setting for the model request.
		 */
		REQUEST_TOP_K {
			@Override
			public String asString() {
				return "gen_ai.request.top_k";
			}
		},

		/**
		 * Top-P sampling setting for the model request.
		 */
		REQUEST_TOP_P {
			@Override
			public String asString() {
				return "gen_ai.request.top_p";
			}
		}

	}

	/**
	 * Model response, high-cardinality observation key names for model operations.
	 */
	public enum ResponseHighCardinalityKeyNames implements KeyName {

		/**
		 * General reason the model stopped generating tokens.
		 */
		RESPONSE_FINISH_REASON {
			@Override
			public String asString() {
				return "gen_ai.response.finish_reason";
			}
		},

		/**
		 * Unique identifier for the AI operation.
		 */
		RESPONSE_ID {
			@Override
			public String asString() {
				return "gen_ai.response.id";
			}
		},

		/**
		 * Name of the model that generated the response.
		 */
		RESPONSE_MODEL {
			@Override
			public String asString() {
				return "gen_ai.response.model";
			}
		}

	}

	/**
	 * Model usage, high-cardinality observation key names for model operations.
	 */
	public enum UsageHighCardinalityKeyNames implements KeyName {

		/**
		 * Number of tokens used in the model response.
		 */
		USAGE_COMPLETION_TOKENS {
			@Override
			public String asString() {
				return "gen_ai.usage.completion_tokens";
			}
		},

		/**
		 * Number of tokens used in the model input or prompt.
		 */
		USAGE_PROMPT_TOKENS {
			@Override
			public String asString() {
				return "gen_ai.usage.prompt_tokens";
			}
		}

	}

	/**
	 * Model content, high-cardinality observation key names for model operations.
	 */
	public enum ContentHighCardinalityKeyNames implements KeyName {

		/**
		 * The full prompt sent to the model.
		 */
		PROMPT {
			@Override
			public String asString() {
				return "gen_ai.prompt";
			}
		},

		/**
		 * The full response received from the model.
		 */
		COMPLETION {
			@Override
			public String asString() {
				return "gen_ai.completion";
			}
		}

	}

}
