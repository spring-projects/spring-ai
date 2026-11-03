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

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SystemOneDecisionMapper}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class SystemOneDecisionMapperTests {

	private static final String MODEL = "d1-omni:600m";

	private static final JsonMapper jsonMapper = JsonMapper.builder().build();

	private static final ScoreQuestion SATISFACTION = ScoreQuestion.of("How satisfied is the customer?", "unhappy",
			"neutral", "happy");

	private static final DecisionPrompt SATISFACTION_PROMPT = DecisionPrompt.builder()
		.text("This is the third time my order arrives late.")
		.question("satisfaction", SATISFACTION)
		.build();

	@Test
	void textInputBecomesStringState() {
		JsonContent state = SystemOneDecisionMapper
			.state(DecisionInput.text("Where is my order? I ordered it two weeks ago."));

		JsonAssertions.assertThatJson(toJson(state)).isEqualTo("\"Where is my order? I ordered it two weeks ago.\"");
	}

	@Test
	void textPartsAreJoined() {
		JsonContent state = SystemOneDecisionMapper.state(DecisionInput.content(
				TextPart.of("My new headphones arrived yesterday, "), TextPart.of("but the left side doesn't work.")));

		JsonAssertions.assertThatJson(toJson(state))
			.isEqualTo("\"My new headphones arrived yesterday, but the left side doesn't work.\"");
	}

	@Test
	void fieldsInputBecomesObjectStateInFieldOrder() {
		Map<String, Object> customer = new LinkedHashMap<>();
		customer.put("message", "This is the third time my order arrives late.");
		customer.put("orders", 12);
		customer.put("items", List.of("headphones", "charger", "case"));
		customer.put("note", List.of(TextPart.of("Prefers delivery "), TextPart.of("on Saturdays.")));
		customer.put("status", TextPart.of("Order shipped."));
		customer.put("coupon", null);

		String json = toJson(SystemOneDecisionMapper.state(DecisionInput.fields(customer)));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"message": "This is the third time my order arrives late.", "orders": 12,
				"items": ["headphones", "charger", "case"],
				"note": "Prefers delivery on Saturdays.", "status": "Order shipped.",
				"coupon": null}""");
		assertThat(json.indexOf("message")).isLessThan(json.indexOf("orders"));
		assertThat(json.indexOf("orders")).isLessThan(json.indexOf("coupon"));
	}

	@Test
	void conversationInputBecomesMessagesStateWithToolCallsAndResults() {
		var messages = List.of(new UserMessage("When will my order arrive?"), AssistantMessage.builder()
			.content("Let me check.")
			.reasoning(ReasoningPart.of("The user wants the delivery date."))
			.toolCalls(List
				.of(new AssistantMessage.ToolCall("call_1", "function", "get_delivery_date", "{\"order\":\"A-1001\"}")))
			.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "get_delivery_date", "Friday")))
					.build(),
				new AssistantMessage("Your order will arrive on Friday."));

		String json = toJson(SystemOneDecisionMapper.state(DecisionInput.conversation(messages)));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"messages": [
				{"role": "user", "content": "When will my order arrive?"},
				{"role": "assistant", "content": "Let me check.",
				"tool_calls": [{"id": "call_1", "name": "get_delivery_date", "arguments": {"order": "A-1001"}}]},
				{"role": "tool", "tool_results": [{"id": "call_1", "name": "get_delivery_date", "result": "Friday"}]},
				{"role": "assistant", "content": "Your order will arrive on Friday."}]}""");
		assertThat(json).doesNotContain("delivery date.");
	}

	@Test
	void toolCallArgumentsThatAreNotJsonObjectsAreSentAsIs() {
		var messages = List.of(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "track_package", "A-1001")))
			.build());

		String json = toJson(SystemOneDecisionMapper.state(DecisionInput.conversation(messages)));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"messages": [{"role": "assistant",
				"tool_calls": [{"id": "call_1", "name": "track_package", "arguments": "A-1001"}]}]}""");
	}

	@Test
	void questionsBecomeNoulChoiceAndScore() {
		DecisionPrompt prompt = DecisionPrompt.builder()
			.text("My new headphones arrived yesterday, but the left side doesn't work.")
			.question("defect",
					YesNoQuestion.builder()
						.text("Does the customer report a defect?")
						.whenYes("Part of the item does not work as it should")
						.whenNo("The item works, and the message is about something else")
						.build())
			.question("refund", YesNoQuestion.of("Does the customer ask for a refund?"))
			.question("topic",
					ChoiceQuestion.builder()
						.text("What is the message about?")
						.option("delivery", "Where an order is, or when it will arrive")
						.option("product")
						.build())
			.question("satisfaction",
					ScoreQuestion.builder()
						.text("How satisfied is the customer?")
						.level("unhappy", "The customer complains")
						.level("neutral")
						.level("happy")
						.build())
			.build();

		String json = toJson(SystemOneDecisionMapper.questions(prompt));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"defect": {"type": "noul", "instructions": "Does the customer report a defect?",
				"criteria": {"true": "Part of the item does not work as it should",
				"false": "The item works, and the message is about something else"}},
				"refund": {"type": "noul", "instructions": "Does the customer ask for a refund?"},
				"topic": {"type": "choice", "instructions": "What is the message about?",
				"criteria": {"delivery": "Where an order is, or when it will arrive", "product": null}},
				"satisfaction": {"type": "score", "instructions": "How satisfied is the customer?",
				"criteria": [{"label": "unhappy", "description": "The customer complains"}, "neutral",
				"happy"]}}""");
	}

	@Test
	void responseCarriesAnswersAndMetadata() {
		DecisionPrompt prompt = DecisionPrompt.builder()
			.text("My new headphones arrived yesterday, but the left side doesn't work.")
			.question("defect", YesNoQuestion.of("Does the customer report a defect?"))
			.question("topic",
					ChoiceQuestion.builder()
						.text("What is the message about?")
						.option("product")
						.option("delivery")
						.build())
			.question("satisfaction", SATISFACTION)
			.build();
		Map<String, org.springaicommunity.typesafe.response.Answer> answers = new LinkedHashMap<>();
		answers.put("defect", new NoulAnswer(0.95));
		answers.put("topic", new ChoiceAnswer("product", Map.of("product", 0.75, "delivery", 0.25), 0.7));
		answers.put("satisfaction", new ScoreAnswer(0.8, Map.of(), Map.of(0, 0.3, 1, 0.6, 2, 0.1), 0.4));

		DecisionResponse response = SystemOneDecisionMapper.response(prompt,
				new SystemOneResponse(MODEL, answers, new Usage(20, 1), null));

		assertThat(response.getYesNo("defect").getProbability()).isEqualTo(0.95);
		assertThat(response.getYesNo("defect").isYes()).isTrue();
		assertThat(response.getChoice("topic").getValue()).isEqualTo("product");
		assertThat(response.getChoice("topic").getProbabilities()).containsEntry("product", 0.75)
			.containsEntry("delivery", 0.25);
		assertThat(response.getChoice("topic").getConfidence()).isEqualTo(0.7);
		assertThat(response.getScore("satisfaction").getValue()).isEqualTo(0.8);
		assertThat(response.getScore("satisfaction").getProbabilities()).containsExactly(0.3, 0.6, 0.1);
		assertThat(response.getScore("satisfaction").getNearestLevel()).isEqualTo(1);
		assertThat(response.getScore("satisfaction").getConfidence()).isEqualTo(0.4);
		assertThat(response.getMetadata().getId()).isNull();
		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(20);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(1);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(21);
	}

	@Test
	void scoreProbabilitiesArePaddedToTheQuestionLevels() {
		DecisionResponse response = SystemOneDecisionMapper.response(SATISFACTION_PROMPT, new SystemOneResponse(MODEL,
				Map.of("satisfaction", new ScoreAnswer(1.7, Map.of(), Map.of(0, 0.15, 2, 0.85), 0.5)), Usage.EMPTY));

		assertThat(response.getScore("satisfaction").getProbabilities()).containsExactly(0.15, 0.0, 0.85);
	}

	private static String toJson(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

}
