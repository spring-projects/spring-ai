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

package org.springframework.ai.tool.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

/**
 * Tool calling observation documentation.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum ToolCallingObservationDocumentation implements ObservationDocumentation {

	/**
	 * Tool calling observations.
	 */
	TOOL_CALL {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultToolCallingObservationConvention.class;
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
	 * Low cardinality key names.
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
		 * The provider responsible for the operation.
		 */
		AI_PROVIDER {
			@Override
			public String asString() {
				return AiObservationAttributes.AI_PROVIDER.value();
			}
		},

		/**
		 * Spring AI kind.
		 */
		SPRING_AI_KIND {
			@Override
			public String asString() {
				return "spring.ai.kind";
			}
		},

		/**
		 * The name of the tool.
		 */
		TOOL_DEFINITION_NAME {
			@Override
			public String asString() {
				return "spring.ai.tool.definition.name";
			}
		},

	}

	/**
	 * High cardinality key names.
	 */
	public enum HighCardinalityKeyNames implements KeyName {

		/**
		 * Description of the tool.
		 */
		TOOL_DEFINITION_DESCRIPTION {
			@Override
			public String asString() {
				return "spring.ai.tool.definition.description";
			}
		},

		/**
		 * Schema of the parameters used to call the tool.
		 */
		TOOL_DEFINITION_SCHEMA {
			@Override
			public String asString() {
				return "spring.ai.tool.definition.schema";
			}
		},

		/**
		 * The input arguments to the tool call.
		 */
		TOOL_CALL_ARGUMENTS {
			@Override
			public String asString() {
				return "spring.ai.tool.call.arguments";
			}
		},

		/**
		 * The result of the tool call.
		 */
		TOOL_CALL_RESULT {
			@Override
			public String asString() {
				return "spring.ai.tool.call.result";
			}
		}

	}

}
