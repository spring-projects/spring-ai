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

package org.springframework.ai.decision.observation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DecisionModelMeterObservationHandler}.
 *
 * @author Thomas Vitale
 */
class DecisionModelMeterObservationHandlerTests {

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private final DecisionModelMeterObservationHandler handler = new DecisionModelMeterObservationHandler(
			this.meterRegistry);

	@Test
	void supportsOnlyDecisionContext() {
		assertThat(this.handler.supportsContext(new Observation.Context())).isFalse();
		assertThat(this.handler.supportsContext(context())).isTrue();
	}

	@Test
	void recordsUsageOnce() {
		var context = context();
		context.setResponse(DecisionResponse.builder(context.getRequest())
			.answer("urgent", YesNoAnswer.builder().probability(0.9).build())
			.metadata(DecisionResponseMetadata.builder().usage(new DefaultUsage(10, 2)).build())
			.build());

		this.handler.onStop(context);

		assertThat(this.meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value()).counters()).hasSize(3);
		assertThat(this.meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag("gen_ai.token.type", "total")
			.counter()).isNotNull().satisfies(counter -> assertThat(counter.count()).isEqualTo(12));
	}

	@Test
	void ignoresMissingResponse() {
		this.handler.onStop(context());

		assertThat(this.meterRegistry.getMeters()).isEmpty();
	}

	private static DecisionModelObservationContext context() {
		var prompt = DecisionPrompt.builder().text("Help!").question("urgent", YesNoQuestion.of("Urgent?")).build();
		return DecisionModelObservationContext.builder().decisionPrompt(prompt).provider("superprovider").build();
	}

}
