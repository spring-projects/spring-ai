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

package org.springframework.ai.openai.decision;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonField;
import com.openai.core.RequestOptions;
import com.openai.core.http.HttpResponseFor;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputPart;
import com.openai.services.blocking.DecisionService;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
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
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.observation.DefaultDecisionModelObservationConvention;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiDecisionOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiDecisionModel}.
 *
 * @author Thomas Vitale
 */
class OpenAiDecisionModelTests {

	private final OpenAIClient client = mock(OpenAIClient.class);

	private final DecisionService decisionService = mock(DecisionService.class);

	private final DecisionService.WithRawResponse rawDecisionService = mock(DecisionService.WithRawResponse.class);

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	@BeforeEach
	void setUp() {
		when(this.client.decisions()).thenReturn(this.decisionService);
		when(this.decisionService.withRawResponse()).thenReturn(this.rawDecisionService);
	}

	@Test
	void builderDefaults() {
		OpenAiDecisionModel model = OpenAiDecisionModel.builder().openAiClient(this.client).build();

		assertThat(model.getOptions().getModel()).isEqualTo(OpenAiDecisionOptions.DEFAULT_DECISION_MODEL);
		assertThat(model.mutate().build().getOptions()).isEqualTo(model.getOptions());

		OpenAiDecisionModel withOptions = OpenAiDecisionModel.builder()
			.openAiClient(this.client)
			.options(OpenAiDecisionOptions.builder().safetyIdentifier("user-1").build())
			.build();
		assertThat(withOptions.getOptions().getModel()).isEqualTo(OpenAiDecisionOptions.DEFAULT_DECISION_MODEL);
		assertThat(withOptions.getOptions().getSafetyIdentifier()).isEqualTo("user-1");
	}

	@Test
	void optionsMutateAndCombine() {
		var defaults = OpenAiDecisionOptions.builder()
			.model("m1")
			.apiKey("k1")
			.customHeaders(Map.of("a", "1"))
			.safetyIdentifier("user-1")
			.build();
		var runtime = OpenAiDecisionOptions.builder().model("m2").customHeaders(Map.of("b", "2")).build();

		var combined = defaults.mutate().combineWith(runtime.mutate()).build();

		assertThat(combined.getModel()).isEqualTo("m2");
		assertThat(combined.getApiKey()).isEqualTo("k1");
		assertThat(combined.getCustomHeaders()).containsEntry("a", "1").containsEntry("b", "2");
		assertThat(combined.getSafetyIdentifier()).isEqualTo("user-1");
		assertThat(defaults.mutate().combineWith(DecisionOptions.builder().model("m3")).build().getModel())
			.isEqualTo("m3");
		assertThat(defaults.mutate().build()).isEqualTo(defaults);
	}

	@Test
	void mapsTextInputAndQuestions() {
		stubResponse(Decision.builder()
			.addAnswer(Decision.Answer.Predicate.builder().name("urgent").probability(0.9).build())
			.addAnswer(Decision.Answer.Choice.builder()
				.name("team")
				.choice("technical")
				.confidence(0.8)
				.addProbability(Decision.Answer.Choice.Probability.builder().value("billing").probability(0.3).build())
				.addProbability(
						Decision.Answer.Choice.Probability.builder().value("technical").probability(0.7).build())
				.build())
			.addAnswer(Decision.Answer.Score.builder()
				.name("mood")
				.score(1.6)
				.confidence(0.75)
				.addProbability(
						Decision.Answer.Score.Probability.builder().label("calm").value(0L).probability(0.1).build())
				.addProbability(
						Decision.Answer.Score.Probability.builder().label("angry").value(2L).probability(0.7).build())
				.addProbability(
						Decision.Answer.Score.Probability.builder().label("annoyed").value(1L).probability(0.2).build())
				.build())
			.model("gpt-6-luna-2026")
			.usage(usage(12, 3))
			.build());

		DecisionResponse response = model().call(DecisionPrompt.builder()
			.text("Help! My payouts are late")
			.question("urgent",
					YesNoQuestion.builder().text("Is it urgent?").whenYes("Money is blocked").whenNo("Idle").build())
			.question("team",
					ChoiceQuestion.builder()
						.text("Which team?")
						.option("billing", "Payments")
						.option("technical")
						.build())
			.question("mood",
					ScoreQuestion.builder()
						.text("Mood?")
						.level("calm")
						.level("annoyed", "Mildly")
						.level("angry")
						.build())
			.build());

		DecisionCreateParams params = sentParams();
		assertThat(params.model()).isEqualTo(OpenAiDecisionOptions.DEFAULT_DECISION_MODEL);
		assertThat(params.input().asString()).isEqualTo("Help! My payouts are late");
		assertThat(params.safetyIdentifier()).isEmpty();
		assertThat(params.questions()).hasSize(3);

		var predicate = params.questions().get(0).asPredicate();
		assertThat(predicate.name()).contains("urgent");
		assertThat(predicate.instructions())
			.isEqualTo("Is it urgent?\nAnswer \"yes\" if: Money is blocked\nAnswer \"no\" if: Idle");

		var choice = params.questions().get(1).asChoice();
		assertThat(choice.name()).contains("team");
		assertThat(choice.instructions()).isEqualTo("Which team?");
		assertThat(choice.choices()).hasSize(2);
		assertThat(choice.choices().get(0).value().asString()).isEqualTo("billing");
		assertThat(choice.choices().get(0).description()).contains("Payments");
		assertThat(choice.choices().get(1).value().asString()).isEqualTo("technical");
		assertThat(choice.choices().get(1).description()).isEmpty();

		var score = params.questions().get(2).asScore();
		assertThat(score.name()).contains("mood");
		assertThat(score.levels()).extracting(DecisionCreateParams.Question.Score.Level::label)
			.containsExactly("calm", "annoyed", "angry");
		assertThat(score.levels().get(1).description()).contains("Mildly");

		assertThat(response.getYesNo("urgent").getProbability()).isEqualTo(0.9);
		assertThat(response.getChoice("team").getValue()).isEqualTo("technical");
		assertThat(response.getChoice("team").getProbabilities()).containsEntry("billing", 0.3)
			.containsEntry("technical", 0.7);
		assertThat(response.getChoice("team").getConfidence()).isEqualTo(0.8);
		assertThat(response.getScore("mood").getValue()).isEqualTo(1.6);
		assertThat(response.getScore("mood").getProbabilities()).containsExactly(0.1, 0.2, 0.7);
		assertThat(response.getScore("mood").getConfidence()).isEqualTo(0.75);
		assertThat(response.getMetadata().getId()).isEqualTo("req_1");
		assertThat(response.getMetadata().getModel()).isEqualTo("gpt-6-luna-2026");
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(12);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(3);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(15);
	}

	@Test
	void mapsRefusalAndNamelessAnswersByPosition() {
		stubResponse(Decision.builder()
			.addAnswer(Decision.Answer.Predicate.builder()
				.name(JsonField.<String>ofNullable(null))
				.probability(0.2)
				.build())
			.addAnswer(Decision.Answer.Refusal.builder().name(JsonField.<String>ofNullable(null)).build())
			.model("gpt-6-luna")
			.usage(usage(1, 1))
			.build());

		DecisionResponse response = model().call(DecisionPrompt.builder()
			.text("text")
			.question("spam", YesNoQuestion.of("Spam?"))
			.question("secret", YesNoQuestion.of("Secret?"))
			.build());

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getAnswers().keySet()).containsExactly("spam", "secret");
		assertThat(response.getYesNo("spam").isYes()).isFalse();
		assertThat(response.isRefused("secret")).isTrue();
		assertThat(response.getAnswer("secret", RefusalAnswer.class)).isEqualTo(RefusalAnswer.builder().build());
	}

	@Test
	void mapsTextOnlyContentAsString() {
		stubResponse(yesNoResponse());

		model().call(DecisionInput.content(TextPart.of("What is "), TextPart.of("this?")),
				Map.of("cat", YesNoQuestion.of("A cat?")));

		assertThat(sentParams().input().asString()).isEqualTo("What is this?");
	}

	@Test
	void mapsContentPartsInOrder() {
		stubResponse(yesNoResponse());
		var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build();

		model().call(DecisionInput.content(TextPart.of("What is this?"), MediaPart.of(image), TextPart.of("And why?")),
				Map.of("cat", YesNoQuestion.of("A cat?")));

		List<DecisionInputPart> parts = sentParts();
		assertThat(parts).hasSize(3);
		assertThat(parts.get(0).asInputText().text()).isEqualTo("What is this?");
		assertThat(parts.get(1).asInputImage().imageUrl()).isEqualTo("data:image/png;base64,AQID");
		assertThat(parts.get(2).asInputText().text()).isEqualTo("And why?");
	}

	@Test
	void rejectsUnsupportedContentParts() {
		assertThatThrownBy(() -> model().call(DecisionInput.content(TextPart.of("x"), ReasoningPart.of("hmm")),
				Map.of("q", YesNoQuestion.of("?"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ReasoningPart");
	}

	@Test
	void mapsFieldsAsLabeledParts() {
		stubResponse(yesNoResponse());
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("subject", "Payouts late");
		fields.put("attempts", 3);
		fields.put("tags", List.of("billing", "urgent"));
		var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(new byte[] { 1 }).build();
		fields.put("screenshot", MediaPart.of(image));
		fields.put("receipt", List.of(TextPart.of("Order 42"), MediaPart.of(image)));

		model().call(DecisionInput.fields(fields), Map.of("urgent", YesNoQuestion.of("Urgent?")));

		List<DecisionInputPart> parts = sentParts();
		assertThat(parts).hasSize(8);
		assertThat(parts.get(0).asInputText().text()).isEqualTo("subject: Payouts late");
		assertThat(parts.get(1).asInputText().text()).isEqualTo("attempts: 3");
		assertThat(parts.get(2).asInputText().text()).isEqualTo("tags: [\"billing\",\"urgent\"]");
		assertThat(parts.get(3).asInputText().text()).isEqualTo("screenshot:");
		assertThat(parts.get(4).asInputImage().imageUrl()).isEqualTo("data:image/jpeg;base64,AQ==");
		assertThat(parts.get(5).asInputText().text()).isEqualTo("receipt:");
		assertThat(parts.get(6).asInputText().text()).isEqualTo("Order 42");
		assertThat(parts.get(7).asInputImage().imageUrl()).isEqualTo("data:image/jpeg;base64,AQ==");
	}

	@Test
	void mapsConversationAsOrderedParts() {
		stubResponse(yesNoResponse());
		var image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1 }).build();
		var messages = List.of(new SystemMessage("Be brief"), UserMessage.builder().text("Look").media(image).build(),
				new AssistantMessage("A cat"),
				UserMessage.builder().part(MediaPart.of(image)).part(TextPart.of("And this one?")).build());

		model().call(DecisionInput.conversation(messages), Map.of("ok", YesNoQuestion.of("Answered well?")));

		List<DecisionInputPart> parts = sentParts();
		assertThat(parts).hasSize(7);
		assertThat(parts.get(0).asInputText().text()).isEqualTo("system: Be brief");
		assertThat(parts.get(1).asInputText().text()).isEqualTo("user: Look");
		assertThat(parts.get(2).asInputImage().imageUrl()).isEqualTo("data:image/png;base64,AQ==");
		assertThat(parts.get(3).asInputText().text()).isEqualTo("assistant: A cat");
		assertThat(parts.get(4).asInputText().text()).isEqualTo("user: ");
		assertThat(parts.get(5).asInputImage().imageUrl()).isEqualTo("data:image/png;base64,AQ==");
		assertThat(parts.get(6).asInputText().text()).isEqualTo("And this one?");
	}

	@Test
	void mapsToolCallsAndResultsAsLines() {
		stubResponse(yesNoResponse());
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

		model().call(DecisionInput.conversation(messages), Map.of("ok", YesNoQuestion.of("Answered well?")));

		assertThat(sentParts()).extracting(part -> part.asInputText().text())
			.containsExactly("user: Weather in Paris?",
					"assistant: Let me check.\n[calls get_weather {\"city\":\"Paris\"}]", "tool: get_weather: rainy",
					"assistant: It is rainy in Paris.");
	}

	@Test
	void skipsMessagesWithNothingToSendAndNamesToolResultsByIdWithoutName() {
		stubResponse(yesNoResponse());
		var messages = List.of(new UserMessage("Weather in Paris?"),
				AssistantMessage.builder().reasoning(ReasoningPart.of("Thinking only.")).build(),
				ToolResponseMessage.builder()
					.parts(List
						.of(new ToolResultPart("call_1", null, List.of(TextPart.of("rainy")), false, null, Map.of())))
					.build(),
				ToolResponseMessage.builder()
					.parts(List
						.of(new ToolResultPart(null, null, List.of(TextPart.of("sunny")), false, null, Map.of())))
					.build());

		model().call(DecisionInput.conversation(messages), Map.of("ok", YesNoQuestion.of("Answered well?")));

		assertThat(sentParts()).extracting(part -> part.asInputText().text())
			.containsExactly("user: Weather in Paris?", "tool: call_1: rainy", "tool: sunny");
	}

	@Test
	void mutateKeepsHttpClientCustomizers() {
		List<String> applied = new ArrayList<>();
		OpenAiHttpClientBuilderCustomizer customizer = builder -> applied.add("applied");
		OpenAiDecisionModel model = OpenAiDecisionModel.builder()
			.options(OpenAiDecisionOptions.builder().apiKey("test").build())
			.httpClientBuilderCustomizer(customizer)
			.build();
		assertThat(applied).hasSize(1);

		model.mutate().openAiClient(null).build();

		assertThat(applied).hasSize(2);
	}

	@Test
	void passesDataUrlsThroughAndRejectsHostedImageUrls() {
		stubResponse(yesNoResponse());
		var dataUrl = new Media(MimeTypeUtils.IMAGE_PNG, URI.create("data:image/png;base64,AQID"));
		model().call(DecisionInput.content(TextPart.of("x"), MediaPart.of(dataUrl)),
				Map.of("q", YesNoQuestion.of("?")));
		assertThat(sentParts().get(1).asInputImage().imageUrl()).isEqualTo("data:image/png;base64,AQID");

		var hosted = new Media(MimeTypeUtils.IMAGE_JPEG, URI.create("https://example.com/shot.jpg"));
		assertThatThrownBy(() -> model().call(DecisionInput.content(TextPart.of("x"), MediaPart.of(hosted)),
				Map.of("q", YesNoQuestion.of("?"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("inline base64 images")
			.hasMessageContaining("https://example.com/shot.jpg");
	}

	@Test
	void rejectsNonImageMedia() {
		var audio = Media.builder().mimeType(MimeTypeUtils.parseMimeType("audio/wav")).data(new byte[] { 1 }).build();

		assertThatThrownBy(() -> model().call(DecisionInput.content(TextPart.of("x"), MediaPart.of(audio)),
				Map.of("q", YesNoQuestion.of("?"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Only images");
	}

	@Test
	void mergesRuntimeOptions() {
		stubResponse(yesNoResponse());
		OpenAiDecisionModel model = OpenAiDecisionModel.builder()
			.openAiClient(this.client)
			.options(OpenAiDecisionOptions.builder().model("default-model").safetyIdentifier("user-1").build())
			.build();

		model.call(DecisionPrompt.builder()
			.text("x")
			.question("q", YesNoQuestion.of("?"))
			.options(DecisionOptions.builder().model("runtime-model").build())
			.build());
		assertThat(sentParams().model()).isEqualTo("runtime-model");
		assertThat(sentParams().safetyIdentifier()).contains("user-1");

		model.call(DecisionPrompt.builder()
			.text("x")
			.question("q", YesNoQuestion.of("?"))
			.options(OpenAiDecisionOptions.builder().safetyIdentifier("user-2").build())
			.build());
		assertThat(sentParams().model()).isEqualTo("default-model");
		assertThat(sentParams().safetyIdentifier()).contains("user-2");
	}

	@Test
	void rejectsResponseNotMatchingTheQuestions() {
		stubResponse(Decision.builder()
			.addAnswer(Decision.Answer.Choice.builder()
				.name("team")
				.choice("sales")
				.confidence(0.5)
				.probabilities(List.of())
				.build())
			.model("gpt-6-luna")
			.usage(usage(1, 1))
			.build());

		assertThatThrownBy(
				() -> model().call("x", Map.of("team", ChoiceQuestion.of("Which team?", "billing", "technical"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("'sales'");
	}

	@Test
	void recordsObservation() {
		stubResponse(yesNoResponse());

		model().call("x", Map.of("q", YesNoQuestion.of("?")));

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("decision " + OpenAiDecisionOptions.DEFAULT_DECISION_MODEL)
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(),
					AiOperationType.DECISION.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), AiProvider.OPENAI.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value(),
					OpenAiDecisionOptions.DEFAULT_DECISION_MODEL)
			.hasLowCardinalityKeyValue(AiObservationAttributes.RESPONSE_MODEL.value(), "gpt-6-luna")
			.hasHighCardinalityKeyValue(AiObservationAttributes.RESPONSE_ID.value(), "req_1")
			.hasHighCardinalityKeyValue(AiObservationAttributes.USAGE_INPUT_TOKENS.value(), "1")
			.hasHighCardinalityKeyValue(AiObservationAttributes.USAGE_OUTPUT_TOKENS.value(), "1")
			.hasHighCardinalityKeyValue(AiObservationAttributes.USAGE_TOTAL_TOKENS.value(), "2")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void leavesResponseIdUnsetWithoutRequestIdHeader() {
		stubResponse(yesNoResponse(), null);

		DecisionResponse response = model().call("x", Map.of("q", YesNoQuestion.of("?")));

		assertThat(response.getMetadata().getId()).isNull();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultDecisionModelObservationConvention.DEFAULT_NAME)
			.that()
			.doesNotHaveHighCardinalityKeyValueWithKey(AiObservationAttributes.RESPONSE_ID.value());
	}

	@Test
	void normalizesRequestIdHeader() {
		stubResponse(yesNoResponse(), " req_2 ");
		assertThat(model().call("x", Map.of("q", YesNoQuestion.of("?"))).getMetadata().getId()).isEqualTo("req_2");

		stubResponse(yesNoResponse(), "  ");
		assertThat(model().call("x", Map.of("q", YesNoQuestion.of("?"))).getMetadata().getId()).isNull();
	}

	@Test
	void usesCustomObservationConvention() {
		stubResponse(yesNoResponse());
		var convention = new DefaultDecisionModelObservationConvention() {
			@Override
			public String getName() {
				return "custom.decision";
			}
		};

		OpenAiDecisionModel.builder()
			.openAiClient(this.client)
			.observationRegistry(this.observationRegistry)
			.observationConvention(convention)
			.build()
			.call("x", Map.of("q", YesNoQuestion.of("?")));

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo("custom.decision")
			.that()
			.hasBeenStopped();
	}

	private OpenAiDecisionModel model() {
		return OpenAiDecisionModel.builder()
			.openAiClient(this.client)
			.observationRegistry(this.observationRegistry)
			.build();
	}

	private void stubResponse(Decision decision) {
		stubResponse(decision, "req_1");
	}

	@SuppressWarnings("unchecked")
	private void stubResponse(Decision decision, @Nullable String requestId) {
		HttpResponseFor<Decision> rawResponse = mock(HttpResponseFor.class);
		when(rawResponse.parse()).thenReturn(decision);
		when(rawResponse.requestId()).thenReturn(Optional.ofNullable(requestId));
		when(this.rawDecisionService.create(any(DecisionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(rawResponse);
	}

	private DecisionCreateParams sentParams() {
		ArgumentCaptor<DecisionCreateParams> captor = ArgumentCaptor.forClass(DecisionCreateParams.class);
		verify(this.rawDecisionService, atLeastOnce()).create(captor.capture(), any(RequestOptions.class));
		return captor.getValue();
	}

	private List<DecisionInputPart> sentParts() {
		var messages = sentParams().input().asDecisionInputMessages();
		assertThat(messages).hasSize(1);
		return messages.get(0).content().asParts();
	}

	private static Decision yesNoResponse() {
		return Decision.builder()
			.addAnswer(Decision.Answer.Predicate.builder()
				.name(JsonField.<String>ofNullable(null))
				.probability(0.5)
				.build())
			.model("gpt-6-luna")
			.usage(usage(1, 1))
			.build();
	}

	private static Decision.Usage usage(long input, long output) {
		return Decision.Usage.builder()
			.inputTokens(input)
			.outputTokens(output)
			.totalTokens(input + output)
			.inputTokensDetails(
					Decision.Usage.InputTokensDetails.builder().cachedTokens(0L).cacheWriteTokens(0L).build())
			.outputTokensDetails(Decision.Usage.OutputTokensDetails.builder().reasoningTokens(0L).build())
			.build();
	}

}
