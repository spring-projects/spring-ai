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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AdvisorObservationContext}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class AdvisorObservationContextTests {

	@Test
	void whenMandatoryOptionsThenReturn() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.withAdvisorName("MyName")
			.withAdvisorType(AdvisorObservationContext.Type.BEFORE)
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void missingAdvisorName() {
		assertThatThrownBy(() -> AdvisorObservationContext.builder()
			.withAdvisorType(AdvisorObservationContext.Type.BEFORE)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorName must not be null or empty");
	}

	@Test
	void missingAdvisorType() {
		assertThatThrownBy(() -> AdvisorObservationContext.builder().withAdvisorName("MyName").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorType must not be null");
	}

}
