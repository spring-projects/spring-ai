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
import org.springframework.ai.systemone.api.SystemOneClient;
import org.springframework.ai.systemone.api.exception.SystemOneException;
import org.springframework.ai.systemone.api.question.SystemOneRequest;
import org.springframework.ai.systemone.api.response.NoulAnswer;
import org.springframework.ai.systemone.api.response.SystemOneResponse;
import org.springframework.ai.systemone.api.response.Usage;

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
	SystemOneClient systemOneClient;

	@Captor
	ArgumentCaptor<SystemOneRequest> systemOneRequestCaptor;

	@Test
	void options() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(defectResponse("DEFAULT_MODEL", 0.9))
			.willReturn(defectResponse("RUNTIME_MODEL", 0.1));

		// Tests default options
		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		DecisionResponse response = decisionModel.call(orderPrompt(null));

		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo("DEFAULT_MODEL");
		assertThat(response.getMetadata().getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(response.getYesNo("defect").getProbability()).isEqualTo(0.9);

		// Tests runtime options
		response = decisionModel.call(orderPrompt(OllamaDecisionOptions.builder().model("RUNTIME_MODEL").build()));

		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");
		assertThat(response.getMetadata().getModel()).isEqualTo("RUNTIME_MODEL");
		assertThat(response.getYesNo("defect").getProbability()).isEqualTo(0.1);
	}

	@Test
	void portableRuntimeOptionsOverrideTheModel() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(defectResponse("RUNTIME_MODEL", 0.5));

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.build();

		decisionModel.call(orderPrompt(DecisionOptions.builder().model("RUNTIME_MODEL").build()));

		assertThat(this.systemOneRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");
	}

	@Test
	void sendsStateAndQuestions() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(defectResponse(MODEL, 0.9));

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.build();

		decisionModel.call(DecisionPrompt.builder()
			.text("My new headphones arrived yesterday, but the left side doesn't work. Can I get my money back?")
			.question("defect",
					YesNoQuestion.builder()
						.text("Does the customer report a defect?")
						.whenYes("Part of the item does not work as it should")
						.whenNo("The item works, and the message is about something else")
						.build())
			.build());

		JsonAssertions.assertThatJson(jsonMapper.writeValueAsString(this.systemOneRequestCaptor.getValue()))
			.isEqualTo(
					"""
							{"state": "My new headphones arrived yesterday, but the left side doesn't work. Can I get my money back?",
							"model": "d1-omni:600m",
							"questions": {"defect": {"type": "noul", "instructions": "Does the customer report a defect?",
							"criteria": {"true": "Part of the item does not work as it should",
							"false": "The item works, and the message is about something else"}}}}""");
	}

	@Test
	void builderUsesDefaultModelWhenOptionsNameNone() {
		given(this.systemOneClient.systemOne(this.systemOneRequestCaptor.capture()))
			.willReturn(defectResponse(MODEL, 0.9));

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.build();

		decisionModel.call(orderPrompt(null));

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

		assertThatThrownBy(() -> decisionModel.call(orderPrompt(DecisionOptions.builder().model(" ").build())))
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
			.willReturn(defectResponse(MODEL, 0.9));
		var observationRegistry = TestObservationRegistry.create();

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.observationRegistry(observationRegistry)
			.build();

		decisionModel.call(orderPrompt(null));

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
			.willThrow(new SystemOneException("model not found"));
		var observationRegistry = TestObservationRegistry.create();

		var decisionModel = OllamaDecisionModel.builder()
			.ollamaApi(this.ollamaApi)
			.systemOneClient(this.systemOneClient)
			.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
			.observationRegistry(observationRegistry)
			.build();

		assertThatThrownBy(() -> decisionModel.call(orderPrompt(null))).isInstanceOf(SystemOneException.class)
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

	private static DecisionPrompt orderPrompt(@Nullable DecisionOptions options) {
		return DecisionPrompt.builder()
			.text("Where is my order? I ordered it two weeks ago.")
			.question("defect", YesNoQuestion.of("Does the customer report a defect?"))
			.options(options)
			.build();
	}

	private static SystemOneResponse defectResponse(String model, double probability) {
		return new SystemOneResponse(model, Map.of("defect", new NoulAnswer(probability)), new Usage(20, 0));
	}

}
