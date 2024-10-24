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

package org.springframework.ai.chat.client.advisor.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public enum AdvisorObservationDocumentation implements ObservationDocumentation {

	/**
	 * AI Advisor observations
	 */
	AI_ADVISOR {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAdvisorObservationConvention.class;
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

	public enum LowCardinalityKeyNames implements KeyName {

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
		 * Advisor type: Before, After or Around.
		 */
		ADVISOR_TYPE {
			@Override
			public String asString() {
				return "spring.ai.advisor.type";
			}
		}

	}

	public enum HighCardinalityKeyNames implements KeyName {

		/**
		 * Advisor name.
		 */
		ADVISOR_NAME {
			@Override
			public String asString() {
				return "spring.ai.advisor.name";
			}
		},
		/**
		 * Advisor order in the advisor chain.
		 */
		ADVISOR_ORDER {
			@Override
			public String asString() {
				return "spring.ai.advisor.order";
			}
		}

	}

}
