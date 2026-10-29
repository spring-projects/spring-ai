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

package org.springframework.ai.systemone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeBadRequestException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.observation.DefaultDecisionModelObservationConvention;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link SystemOneDecisionModel}, against a mocked System One server so
 * that the request the SDK sends and the answers it parses are both covered.
 *
 * @author Christian Tzolov
 */
class SystemOneDecisionModelTests {

	private static final String BASE_URL = "http://systemone.test";

	private static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

	private final RestClient.Builder restClientBuilder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(this.restClientBuilder).build();

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final TypeSafeClient client = TypeSafeClient.builder()
		.baseUrl(BASE_URL)
		.apiKey("key")
		.defaultModel("nimble")
		.retryPolicy(RetryPolicy.noRetry())
		.restClientBuilder(this.restClientBuilder)
		.build();

	private @Nullable String sentBody;

	private SystemOneDecisionModel model() {
		return SystemOneDecisionModel.builder(this.client).observationRegistry(this.observationRegistry).build();
	}

	private void stubResponse(String responseJson, @Nullable String requestId) {
		DefaultResponseCreator response = withSuccess(responseJson, MediaType.APPLICATION_JSON);
		if (requestId != null) {
			response.header(REQUEST_ID_HEADER, requestId);
		}
		this.server.expect(requestTo(BASE_URL + "/v1/systemone"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(request -> this.sentBody = ((MockClientHttpRequest) request).getBodyAsString())
			.andRespond(response);
	}

	private void assertSent(String expectedJson) throws Exception {
		this.server.verify();
		assertThat(this.sentBody).isNotNull();
		JSONAssert.assertEquals(expectedJson, this.sentBody, JSONCompareMode.STRICT);
	}

	private static String yesNoResponse(double probability) {
		return """
				{"model": "nimble", "answers": {"urgent": {"type": "noul", "noul": %s}},
				"usage": {"input_tokens": 12, "output_tokens": 3}}""".formatted(probability);
	}

	@Test
	void sendsTextAndYesNoQuestionWithCriteria() throws Exception {
		stubResponse(yesNoResponse(0.9), "req_1");

		DecisionResponse response = model().call(DecisionPrompt.builder()
			.text("Help! My payouts are late")
			.question("urgent",
					YesNoQuestion.builder()
						.text("Is it urgent?")
						.whenYes("Money is blocked")
						.whenNo("A question")
						.build())
			.build());

		assertSent("""
				{"state": "Help! My payouts are late", "model": "nimble",
				"questions": {"urgent": {"type": "noul", "instructions": "Is it urgent?",
				"criteria": {"true": "Money is blocked", "false": "A question"}}}}""");
		assertThat(response.getYesNo("urgent").getProbability()).isEqualTo(0.9);
		assertThat(response.getYesNo("urgent").isYes()).isTrue();
		assertThat(response.getMetadata().getId()).isEqualTo("req_1");
		assertThat(response.getMetadata().getModel()).isEqualTo("nimble");
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(12);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(3);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(15);
	}

	@Test
	void sendsChoiceAndScoreQuestions() throws Exception {
		stubResponse("""
				{"model": "nimble", "answers": {
				"team": {"type": "choice", "choice": "billing",
				"probabilities": {"billing": 0.7, "other": 0.3}, "confidence": 0.8},
				"mood": {"type": "score", "score": 1.4, "legend": {"0": "calm", "1": "annoyed", "2": "angry"},
				"probabilities": {"0": 0.1, "1": 0.4, "2": 0.5}, "confidence": 0.6}},
				"usage": {}}""", null);

		DecisionResponse response = model().call(DecisionPrompt.builder()
			.text("x")
			.question("team",
					ChoiceQuestion.builder().text("Which team?").option("billing", "Payments").option("other").build())
			.question("mood",
					ScoreQuestion.builder()
						.text("How is the mood?")
						.level("calm", "Just stating facts")
						.level("annoyed")
						.level("angry")
						.build())
			.build());

		assertSent("""
				{"state": "x", "model": "nimble", "questions": {
				"team": {"type": "choice", "instructions": "Which team?",
				"criteria": {"billing": "Payments", "other": null}},
				"mood": {"type": "score", "instructions": "How is the mood?",
				"criteria": [{"label": "calm", "description": "Just stating facts"}, "annoyed", "angry"]}}}""");
		assertThat(response.getChoice("team").getValue()).isEqualTo("billing");
		assertThat(response.getChoice("team").getProbabilities()).containsEntry("billing", 0.7)
			.containsEntry("other", 0.3);
		assertThat(response.getChoice("team").getConfidence()).isEqualTo(0.8);
		assertThat(response.getScore("mood").getValue()).isEqualTo(1.4);
		assertThat(response.getScore("mood").getProbabilities()).containsExactly(0.1, 0.4, 0.5);
		assertThat(response.getScore("mood").getNearestLevel()).isEqualTo(2);
		assertThat(response.getScore("mood").getConfidence()).isEqualTo(0.6);
		assertThat(response.getMetadata().getId()).isNull();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isZero();
	}

	@Test
	void sendsFieldsAsObjectInOrderWithPartsAsText() throws Exception {
		stubResponse(yesNoResponse(0.2), null);
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("subject", "Payouts late");
		fields.put("attempts", 3);
		fields.put("tags", List.of("billing", "urgent"));
		fields.put("attachment", List.of(TextPart.of("The receipt: "), TextPart.of("42 EUR")));
		fields.put("note", TextPart.of("VIP"));

		model().call(DecisionInput.fields(fields), Map.of("urgent", YesNoQuestion.of("Is it urgent?")));

		assertSent("""
				{"state": {"subject": "Payouts late", "attempts": 3, "tags": ["billing", "urgent"],
				"attachment": "The receipt: 42 EUR", "note": "VIP"},
				"model": "nimble", "questions": {"urgent": {"type": "noul", "instructions": "Is it urgent?"}}}""");
		assertThat(this.sentBody)
			.satisfies(body -> assertThat(body.indexOf("subject")).isLessThan(body.indexOf("attempts"))
				.isLessThan(body.indexOf("tags")));
	}

	@Test
	void sendsConversationWithToolCallsAndResults() throws Exception {
		stubResponse(yesNoResponse(0.8), null);
		var messages = List.of(new UserMessage("Weather in Paris?"), AssistantMessage.builder()
			.content("Let me check.")
			.reasoning(ReasoningPart.of("The user wants the weather."))
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call_1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "get_weather", "rainy")))
					.build(),
				new AssistantMessage("It is rainy in Paris."));

		model().call(DecisionInput.conversation(messages), Map.of("urgent", YesNoQuestion.of("Answered well?")));

		assertSent("""
				{"state": {"messages": [
				{"role": "user", "content": "Weather in Paris?"},
				{"role": "assistant", "content": "Let me check.",
				"tool_calls": [{"id": "call_1", "name": "get_weather", "arguments": {"city": "Paris"}}]},
				{"role": "tool", "tool_results": [{"id": "call_1", "name": "get_weather", "result": "rainy"}]},
				{"role": "assistant", "content": "It is rainy in Paris."}]},
				"model": "nimble", "questions": {"urgent": {"type": "noul", "instructions": "Answered well?"}}}""");
	}

	@Test
	void rejectsMediaInEveryInputKind() {
		var image = MediaPart.of(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(new byte[] { 1 })));
		var questions = Map.of("urgent", YesNoQuestion.of("?"));

		assertThatThrownBy(() -> model().call(DecisionInput.content(TextPart.of("x"), image), questions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("carries media");
		assertThatThrownBy(() -> model().call(DecisionInput.fields(Map.of("shot", image)), questions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("field 'shot'");
		var withImage = UserMessage.builder().text("x").media(image.media()).build();
		assertThatThrownBy(() -> model().call(DecisionInput.conversation(List.of(withImage)), questions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("user message carries media");
	}

	@Test
	void promptOptionsOverrideTheModel() throws Exception {
		stubResponse(yesNoResponse(0.5).replace("nimble", "jev-latest"), null);

		DecisionResponse response = model().call(DecisionPrompt.builder()
			.text("x")
			.question("urgent", YesNoQuestion.of("?"))
			.options(DecisionOptions.builder().model("jev-latest").build())
			.build());

		assertThat(this.sentBody).contains("\"model\":\"jev-latest\"");
		assertThat(response.getMetadata().getModel()).isEqualTo("jev-latest");
		assertThat(model().getOptions().getModel()).isEqualTo("nimble");
	}

	@Test
	void builderAppliesDefaults() {
		var built = SystemOneDecisionModel.builder(this.client).build();
		assertThat(built.getOptions().getModel()).isEqualTo("nimble");
		assertThat(built.getProvider()).isEqualTo(SystemOneDecisionModel.DEFAULT_PROVIDER);

		var configured = SystemOneDecisionModel.builder(this.client)
			.options(SystemOneDecisionOptions.builder().model("m").build())
			.provider("laya")
			.build();
		assertThat(configured.getOptions().getModel()).isEqualTo("m");
		assertThat(configured.getProvider()).isEqualTo("laya");
	}

	@Test
	void rejectsBlankModelFromPromptOptions() {
		assertThatThrownBy(() -> model().call(DecisionPrompt.builder()
			.text("x")
			.question("urgent", YesNoQuestion.of("?"))
			.options(DecisionOptions.builder().model(" ").build())
			.build())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("model");
	}

	@Test
	void scoreWithoutProbabilitiesStaysEmpty() {
		stubResponse(
				"""
						{"model": "nimble", "answers": {"mood": {"type": "score", "score": 1.4, "legend": {}, "probabilities": {},
						"confidence": 0.6}}}""",
				null);

		DecisionResponse response = model().call("x",
				Map.of("mood", ScoreQuestion.of("How is the mood?", "calm", "annoyed", "angry")));

		assertThat(response.getScore("mood").getProbabilities()).isEmpty();
		assertThat(response.getScore("mood").getNearestLevel()).isEqualTo(1);
	}

	@Test
	void reportsAnAnswerTheQuestionsDoNotNameByItsName() {
		stubResponse(
				"""
						{"model": "nimble", "answers": {"urgent": {"type": "noul", "noul": 0.5},
						"extra": {"type": "score", "score": 1, "legend": {}, "probabilities": {"0": 0.4, "1": 0.6}, "confidence": 0.5}}}""",
				null);

		assertThatThrownBy(() -> model().call("x", Map.of("urgent", YesNoQuestion.of("?"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no question named 'extra'");
	}

	@Test
	void rejectsUnsupportedPartsInTextAndFieldsInputs() {
		var questions = Map.of("urgent", YesNoQuestion.of("?"));

		assertThatThrownBy(
				() -> model().call(DecisionInput.content(TextPart.of("x"), ReasoningPart.of("r")), questions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported decision input part ReasoningPart");
		assertThatThrownBy(() -> model().call(DecisionInput.fields(Map.of("r", ReasoningPart.of("r"))), questions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("field 'r'");
	}

	@Test
	void sendsConversationEdgeCases() throws Exception {
		stubResponse(yesNoResponse(0.5), null);
		var messages = List.of(AssistantMessage.builder().reasoning(ReasoningPart.of("Thinking only.")).build(),
				AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call_2", "function", "noop", "not json")))
					.build(),
				ToolResponseMessage.builder()
					.parts(List.of(new ToolResultPart(null, null, List.of(TextPart.of("boom")), true, null, Map.of())))
					.build());

		model().call(DecisionInput.conversation(messages), Map.of("urgent", YesNoQuestion.of("?")));

		assertSent("""
				{"state": {"messages": [
				{"role": "assistant", "content": ""},
				{"role": "assistant", "tool_calls": [{"id": "call_2", "name": "noop", "arguments": "not json"}]},
				{"role": "tool", "tool_results": [{"id": null, "name": null, "result": "boom", "error": true}]}]},
				"model": "nimble", "questions": {"urgent": {"type": "noul", "instructions": "?"}}}""");
	}

	@Test
	void sendsNullFieldValuesAsJsonNull() throws Exception {
		stubResponse(yesNoResponse(0.5), null);
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("subject", "x");
		fields.put("note", null);

		model().call(DecisionInput.fields(fields), Map.of("urgent", YesNoQuestion.of("?")));

		assertSent("""
				{"state": {"subject": "x", "note": null}, "model": "nimble",
				"questions": {"urgent": {"type": "noul", "instructions": "?"}}}""");
	}

	@Test
	void failsOnScoreProbabilityOutsideTheLevels() {
		stubResponse("""
				{"model": "nimble", "answers": {"mood": {"type": "score", "score": 1, "legend": {},
				"probabilities": {"0": 0.5, "5": 0.5}, "confidence": 0.5}}}""", null);

		assertThatThrownBy(() -> model().call("x",
				Map.of("mood", ScoreQuestion.of("How is the mood?", "calm", "annoyed", "angry"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("level 5 of 3");
	}

	@Test
	void propagatesServerErrorsAndRecordsTheObservation() {
		this.server.expect(requestTo(BASE_URL + "/v1/systemone"))
			.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"nope\"}"));

		assertThatThrownBy(() -> model().call("x", Map.of("urgent", YesNoQuestion.of("?"))))
			.isInstanceOf(TypeSafeBadRequestException.class)
			.hasMessageContaining("nope");
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasError();
	}

	@Test
	void optionsMutateCompareAndPrint() {
		var options = SystemOneDecisionOptions.builder().model("m").build();

		assertThat(options.mutate().build()).isEqualTo(options).hasSameHashCodeAs(options);
		assertThat(options).isNotEqualTo(SystemOneDecisionOptions.builder().model("n").build());
		assertThat(options.toString()).contains("m");
		assertThat(SystemOneDecisionOptions.builder().build().getModel()).isNull();
	}

	@Test
	void failsOnUnknownAnswerType() {
		stubResponse("""
				{"model": "nimble", "answers": {"urgent": {"type": "rank", "rank": [1, 2]}}}""", null);

		assertThatThrownBy(() -> model().call("x", Map.of("urgent", YesNoQuestion.of("?"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Unsupported answer type for question 'urgent'");
	}

	@Test
	void failsOnMissingAnswer() {
		stubResponse(yesNoResponse(0.5), null);

		assertThatThrownBy(
				() -> model().call("x", Map.of("urgent", YesNoQuestion.of("?"), "spam", YesNoQuestion.of("Spam?"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("missing answer for question 'spam'");
	}

	@Test
	void recordsObservationWithProviderAndResponseId() {
		stubResponse(yesNoResponse(0.9), "req_1");

		model().call("x", Map.of("urgent", YesNoQuestion.of("?")));

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(),
					AiOperationType.DECISION.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), "systemone")
			.hasLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value(), "nimble")
			.hasLowCardinalityKeyValue(AiObservationAttributes.RESPONSE_MODEL.value(), "nimble")
			.hasHighCardinalityKeyValue(AiObservationAttributes.RESPONSE_ID.value(), "req_1");
	}

	@Test
	void providerNameIsConfigurableAndKeptByMutate() {
		stubResponse(yesNoResponse(0.9), null);
		SystemOneDecisionModel ollama = model().mutate().provider("ollama").build();

		ollama.call("x", Map.of("urgent", YesNoQuestion.of("?")));

		assertThat(ollama.getProvider()).isEqualTo("ollama");
		assertThat(ollama.mutate().build().getProvider()).isEqualTo("ollama");
		assertThat(ollama.getOptions().getModel()).isEqualTo("nimble");
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), "ollama");
		assertThatThrownBy(() -> model().mutate().provider(" ")).isInstanceOf(IllegalArgumentException.class);
	}

}
