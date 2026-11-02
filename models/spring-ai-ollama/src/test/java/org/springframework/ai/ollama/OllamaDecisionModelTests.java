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

package org.springframework.ai.ollama;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.observation.DefaultDecisionModelObservationConvention;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ListModelResponse;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.decision.OllamaDecisionOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link OllamaDecisionModel}.
 *
 * @author Thomas Vitale
 */
@ExtendWith(MockitoExtension.class)
class OllamaDecisionModelTests {

	private static final String MODEL = OllamaModel.D1_OMNI_600M.id();

	private static final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Mock
	OllamaApi ollamaApi;

	@Mock
	TypeSafeClient systemOneClient;

	@Captor
	ArgumentCaptor<SystemOneRequest> systemOneRequestCaptor;

	@Test
	void options() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(fleeResponse("DEFAULT_MODEL", 0.9))
			.willReturn(fleeResponse("RUNTIME_MODEL", 0.1));

		// Tests default options
		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		DecisionResponse response = decisionModel.call(dragonPrompt(null));

		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo("DEFAULT_MODEL");
		assertThat(response.getMetadata().getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(response.getYesNo("flee").getProbability()).isEqualTo(0.9);

		// Tests runtime options
		response = decisionModel.call(dragonPrompt(OllamaDecisionOptions.builder().model("RUNTIME_MODEL").build()));

		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");
		assertThat(response.getMetadata().getModel()).isEqualTo("RUNTIME_MODEL");
		assertThat(response.getYesNo("flee").getProbability()).isEqualTo(0.1);
	}

	@Test
	void portableRuntimeOptionsOverrideTheModel() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(fleeResponse("RUNTIME_MODEL", 0.5));

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.build();

		decisionModel.call(dragonPrompt(DecisionOptions.builder().model("RUNTIME_MODEL").build()));

		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");
	}

	@Test
	void sendsStateAndQuestions() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(fleeResponse(MODEL, 0.9));

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.build();

		decisionModel.call(DecisionPrompt.builder()
			.text("An ancient red dragon lands in front of a party of level 1 adventurers.")
			.question("flee",
					YesNoQuestion.builder()
						.text("Should the party run away?")
						.whenYes("The monster vastly outclasses the party")
						.whenNo("The party stands a fair chance in the fight")
						.build())
			.build());

		JsonAssertions.assertThatJson(jsonMapper.writeValueAsString(this.systemOneRequestCaptor.getValue()))
			.isEqualTo("""
					{"state": "An ancient red dragon lands in front of a party of level 1 adventurers.",
					"model": "d1-omni:600m",
					"questions": {"flee": {"type": "noul", "instructions": "Should the party run away?",
					"criteria": {"true": "The monster vastly outclasses the party",
					"false": "The party stands a fair chance in the fight"}}}}""");
	}

	@Test
	void builderUsesDefaultModelWhenOptionsNameNone() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(fleeResponse(MODEL, 0.9));

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.build();

		decisionModel.call(dragonPrompt(null));

		assertThat(decisionModel.getOptions().getModel()).isEqualTo(OllamaDecisionModel.DEFAULT_MODEL);
		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo(OllamaDecisionModel.DEFAULT_MODEL);
		verify(this.systemOneClient, never()).defaultModel();
	}

	@Test
	void builderRejectsMissingClients() {
		assertThatThrownBy(() -> OllamaDecisionModel.builder().systemOneClient(this.systemOneClient).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ollamaApi");
		assertThatThrownBy(() -> OllamaDecisionModel.builder().ollamaApi(this.ollamaApi).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("systemOneClient");
	}

	@Test
	void rejectsBlankRuntimeModel() {
		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.build();

		assertThatThrownBy(() -> decisionModel.call(dragonPrompt(DecisionOptions.builder().model(" ").build())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("model");
		verify(this.systemOneClient, never()).systemOne(any(SystemOneRequest.class));
	}

	@Test
	void doesNotPullModelByDefault() {
		OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.build();

		verifyNoInteractions(this.ollamaApi);
	}

	@Test
	void checksModelAvailabilityWhenPullStrategyIsWhenMissing() {
		given(this.ollamaApi.listModels()).willReturn(new ListModelResponse(
				List.of(new OllamaApi.Model(MODEL, MODEL, Instant.now(), 849L, "1ba59f63e836", null))));

		OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.modelManagementOptions(
					ModelManagementOptions.builder().pullModelStrategy(PullModelStrategy.WHEN_MISSING).build())
			.build();

		verify(this.ollamaApi).listModels();
		verify(this.ollamaApi, never()).pullModel(any());
	}

	@Test
	void recordsObservation() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(fleeResponse(MODEL, 0.9));
		var observationRegistry = TestObservationRegistry.create();

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.observationRegistry(observationRegistry)
			.build();

		decisionModel.call(dragonPrompt(null));

		TestObservationRegistryAssert.assertThat(observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(),
					AiOperationType.DECISION.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), AiProvider.OLLAMA.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value(), MODEL)
			.hasLowCardinalityKeyValue(AiObservationAttributes.RESPONSE_MODEL.value(), MODEL)
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void propagatesClientErrorsAndRecordsThemInTheObservation() {
		given(this.systemOneClient.systemOne(any(SystemOneRequest.class)))
			.willThrow(new TypeSafeException("model not found"));
		var observationRegistry = TestObservationRegistry.create();

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.observationRegistry(observationRegistry)
			.build();

		assertThatThrownBy(() -> decisionModel.call(dragonPrompt(null))).isInstanceOf(TypeSafeException.class)
			.hasMessageContaining("model not found");
		TestObservationRegistryAssert.assertThat(observationRegistry)
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasError();
	}

	@Test
	void decisionOptionsMutateCompareAndPrint() {
		var options = OllamaDecisionOptions.builder().model(MODEL).build();

		assertThat(options.mutate().build()).isEqualTo(options).hasSameHashCodeAs(options);
		assertThat(options).isNotEqualTo(OllamaDecisionOptions.builder().model("other").build());
		assertThat(options.toString()).contains(MODEL);
		assertThat(OllamaDecisionOptions.builder().build().getModel()).isNull();
	}

	private static DecisionPrompt dragonPrompt(@Nullable DecisionOptions options) {
		return DecisionPrompt.builder()
			.text("A dragon is circling above the tavern.")
			.question("flee", YesNoQuestion.of("Should the party run away?"))
			.options(options)
			.build();
	}

	private static SystemOneResponse fleeResponse(String model, double probability) {
		return new SystemOneResponse(model, Map.of("flee", new NoulAnswer(probability)), new Usage(20, 0));
	}

}
