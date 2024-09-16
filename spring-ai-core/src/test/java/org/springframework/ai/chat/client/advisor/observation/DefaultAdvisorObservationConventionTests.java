/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.client.advisor.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation.LowCardinalityKeyNames;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;

/**
 * Unit tests for {@link DefaultAdvisorObservationConvention}.
 *
 * @author Christian Tzolov
 */
class DefaultAdvisorObservationConventionTests {

	private final DefaultAdvisorObservationConvention observationConvention = new DefaultAdvisorObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName()).isEqualTo(DefaultAdvisorObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualName() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.withAdvisorName("MyName")
			.withAdvisorType(AdvisorObservationContext.Type.BEFORE)
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext))
			.isEqualTo("chat_client_advisor my_name_before");
	}

	@Test
	void supportsAdvisorObservationContext() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.withAdvisorName("MyName")
			.withAdvisorType(AdvisorObservationContext.Type.BEFORE)
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.withAdvisorName("MyName")
			.withAdvisorType(AdvisorObservationContext.Type.AFTER)
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.ADVISOR_TYPE.asString(), "AFTER"),
				KeyValue.of(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "chat_client_advisor"));
	}

	@Test
	void shouldHaveKeyValuesWhenDefinedAndResponse() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.withAdvisorName("MyName")
			.withAdvisorType(AdvisorObservationContext.Type.AFTER)
			.build();

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext))
			.contains(KeyValue.of(HighCardinalityKeyNames.ADVISOR_NAME.asString(), "MyName"));
	}

}
