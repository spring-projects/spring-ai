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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

import org.springframework.ai.observation.conventions.AiObservationAttributes;

/**
 * Documented conventions for text-to-speech model observations.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public enum TextToSpeechModelObservationDocumentation implements ObservationDocumentation {

	TEXT_TO_SPEECH_MODEL_OPERATION {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultTextToSpeechModelObservationConvention.class;
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
	 * Low-cardinality observation key names for text-to-speech model operations.
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
	 * High-cardinality observation key names for text-to-speech model operations.
	 */
	public enum HighCardinalityKeyNames implements KeyName {

		// Request

		/**
		 * The voice used to generate the speech audio.
		 */
		REQUEST_TTS_VOICE {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_TTS_VOICE.value();
			}
		},

		/**
		 * The output format of the generated speech audio.
		 */
		REQUEST_TTS_FORMAT {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_TTS_FORMAT.value();
			}
		},

		/**
		 * The speed of the generated speech audio.
		 */
		REQUEST_TTS_SPEED {
			@Override
			public String asString() {
				return AiObservationAttributes.REQUEST_TTS_SPEED.value();
			}
		}

	}

}
