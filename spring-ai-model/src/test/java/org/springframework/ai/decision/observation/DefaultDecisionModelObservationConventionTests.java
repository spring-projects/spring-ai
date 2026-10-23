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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultDecisionModelObservationConvention}.
 *
 * @author Thomas Vitale
 */
class DefaultDecisionModelObservationConventionTests {

	private final DefaultDecisionModelObservationConvention observationConvention = new DefaultDecisionModelObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName())
			.isEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualNameWhenModelIsDefined() {
		var context = context(prompt("jev-1"));
		assertThat(this.observationConvention.getContextualName(context)).isEqualTo("decision jev-1");
	}

	@Test
	void contextualNameWhenModelIsNotDefined() {
		var context = context(prompt(null));
		assertThat(this.observationConvention.getContextualName(context)).isEqualTo("decision");
	}

	@Test
	void supportsOnlyDecisionModelObservationContext() {
		assertThat(this.observationConvention.supportsContext(context(prompt("jev-1")))).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		var context = context(prompt("jev-1"));
		context.setResponse(response(context.getRequest()));

		assertThat(this.observationConvention.getLowCardinalityKeyValues(context)).contains(
				KeyValue.of(AiObservationAttributes.AI_OPERATION_TYPE.value(), "decision"),
				KeyValue.of(AiObservationAttributes.AI_PROVIDER.value(), "superprovider"),
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), "jev-1"),
				KeyValue.of(AiObservationAttributes.RESPONSE_MODEL.value(), "jev-1.13"));
	}

	@Test
	void shouldHaveHighCardinalityKeyValuesWhenDefined() {
		var context = context(prompt("jev-1"));
		context.setResponse(response(context.getRequest()));

		assertThat(this.observationConvention.getHighCardinalityKeyValues(context)).contains(
				KeyValue.of(AiObservationAttributes.RESPONSE_ID.value(), "dec_1"),
				KeyValue.of(AiObservationAttributes.USAGE_INPUT_TOKENS.value(), "10"),
				KeyValue.of(AiObservationAttributes.USAGE_OUTPUT_TOKENS.value(), "2"),
				KeyValue.of(AiObservationAttributes.USAGE_TOTAL_TOKENS.value(), "12"));
	}

	@Test
	void shouldNotHaveKeyValuesWhenMissing() {
		var context = context(prompt(null));

		assertThat(this.observationConvention.getLowCardinalityKeyValues(context)).contains(
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), KeyValue.NONE_VALUE),
				KeyValue.of(AiObservationAttributes.RESPONSE_MODEL.value(), KeyValue.NONE_VALUE));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(context)).isEmpty();
	}

	private static DecisionPrompt prompt(String model) {
		return DecisionPrompt.builder()
			.text("Help!")
			.question("urgent", YesNoQuestion.of("Is it urgent?"))
			.options(model != null ? DecisionOptions.builder().model(model).build() : null)
			.build();
	}

	private static DecisionModelObservationContext context(DecisionPrompt prompt) {
		return DecisionModelObservationContext.builder().decisionPrompt(prompt).provider("superprovider").build();
	}

	private static DecisionResponse response(DecisionPrompt prompt) {
		return DecisionResponse.builder(prompt)
			.answer("urgent", YesNoAnswer.builder().probability(0.9).build())
			.metadata(DecisionResponseMetadata.builder()
				.id("dec_1")
				.model("jev-1.13")
				.usage(new DefaultUsage(10, 2))
				.build())
			.build();
	}

}
