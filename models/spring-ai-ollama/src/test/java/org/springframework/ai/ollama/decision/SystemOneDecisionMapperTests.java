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

package org.springframework.ai.ollama.decision;

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
import org.springaicommunity.typesafe.response.UnknownAnswer;
import org.springaicommunity.typesafe.response.Usage;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SystemOneDecisionMapper}.
 *
 * @author Thomas Vitale
 */
class SystemOneDecisionMapperTests {

	private static final String MODEL = "d1-omni:600m";

	private static final JsonMapper jsonMapper = JsonMapper.builder().build();

	private static final ScoreQuestion DANGER = ScoreQuestion.of("How dangerous is this place?", "tavern", "dungeon",
			"dragon lair");

	private static final DecisionPrompt DANGER_PROMPT = DecisionPrompt.builder()
		.text("A cave that smells of sulphur and burnt knights.")
		.question("danger", DANGER)
		.build();

	@Test
	void textInputBecomesStringState() {
		JsonContent state = SystemOneDecisionMapper
			.state(DecisionInput.text("The innkeeper swears the cellar is full of giant rats."));

		JsonAssertions.assertThatJson(toJson(state))
			.isEqualTo("\"The innkeeper swears the cellar is full of giant rats.\"");
	}

	@Test
	void textPartsAreJoined() {
		JsonContent state = SystemOneDecisionMapper
			.state(DecisionInput.content(TextPart.of("Knock three times, "), TextPart.of("then ask the door nicely.")));

		JsonAssertions.assertThatJson(toJson(state)).isEqualTo("\"Knock three times, then ask the door nicely.\"");
	}

	@Test
	void fieldsInputBecomesObjectStateInFieldOrder() {
		Map<String, Object> quest = new LinkedHashMap<>();
		quest.put("title", "Rescue the bard from the goblins");
		quest.put("reward_gold", 50);
		quest.put("party", List.of("wizard", "dwarf", "two halflings"));
		quest.put("map", List.of(TextPart.of("Head east, "), TextPart.of("then turn left at the troll.")));
		quest.put("warning", TextPart.of("Do not feed the troll."));
		quest.put("patron", null);

		String json = toJson(SystemOneDecisionMapper.state(DecisionInput.fields(quest)));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"title": "Rescue the bard from the goblins", "reward_gold": 50,
				"party": ["wizard", "dwarf", "two halflings"],
				"map": "Head east, then turn left at the troll.", "warning": "Do not feed the troll.",
				"patron": null}""");
		assertThat(json.indexOf("title")).isLessThan(json.indexOf("reward_gold"));
		assertThat(json.indexOf("reward_gold")).isLessThan(json.indexOf("patron"));
	}

	@Test
	void conversationInputBecomesMessagesStateWithToolCallsAndResults() {
		var messages = List.of(new UserMessage("Is it safe to cross the old stone bridge?"), AssistantMessage.builder()
			.content("Let me take a look.")
			.reasoning(ReasoningPart.of("The player is about to do something reckless."))
			.toolCalls(List
				.of(new AssistantMessage.ToolCall("call_1", "function", "inspect", "{\"place\":\"old stone bridge\"}")))
			.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "inspect",
							"A fire demon is standing in the middle of it")))
					.build(),
				new AssistantMessage("Not really. Take the long way round."));

		String json = toJson(SystemOneDecisionMapper.state(DecisionInput.conversation(messages)));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"messages": [
				{"role": "user", "content": "Is it safe to cross the old stone bridge?"},
				{"role": "assistant", "content": "Let me take a look.",
				"tool_calls": [{"id": "call_1", "name": "inspect", "arguments": {"place": "old stone bridge"}}]},
				{"role": "tool", "tool_results": [{"id": "call_1", "name": "inspect",
				"result": "A fire demon is standing in the middle of it"}]},
				{"role": "assistant", "content": "Not really. Take the long way round."}]}""");
		assertThat(json).doesNotContain("reckless");
	}

	@Test
	void toolCallArgumentsThatAreNotJsonObjectsAreSentAsIs() {
		var messages = List.of(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "roll_dice", "2d6+3")))
			.build());

		String json = toJson(SystemOneDecisionMapper.state(DecisionInput.conversation(messages)));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"messages": [{"role": "assistant",
				"tool_calls": [{"id": "call_1", "name": "roll_dice", "arguments": "2d6+3"}]}]}""");
	}

	@Test
	void rejectsMediaInEveryInputKind() {
		var treasureMap = MediaPart.of(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(new byte[] { 1 })));

		assertThatThrownBy(() -> SystemOneDecisionMapper
			.state(DecisionInput.content(TextPart.of("X marks the spot"), treasureMap)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("the input carries media");
		assertThatThrownBy(
				() -> SystemOneDecisionMapper.state(DecisionInput.fields(Map.of("treasure_map", treasureMap))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("field 'treasure_map' carries media");
		var userMessage = UserMessage.builder().text("Where is the treasure?").media(treasureMap.media()).build();
		assertThatThrownBy(() -> SystemOneDecisionMapper.state(DecisionInput.conversation(List.of(userMessage))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("user message carries media");
	}

	@Test
	void rejectsUnsupportedPartsInTextAndFieldsInputs() {
		assertThatThrownBy(() -> SystemOneDecisionMapper
			.state(DecisionInput.content(TextPart.of("A riddle"), ReasoningPart.of("Probably 'time'"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported decision input part ReasoningPart");
		assertThatThrownBy(() -> SystemOneDecisionMapper
			.state(DecisionInput.fields(Map.of("riddle_answer", ReasoningPart.of("Probably 'time'")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("field 'riddle_answer'");
	}

	@Test
	void questionsBecomeNoulChoiceAndScore() {
		DecisionPrompt prompt = DecisionPrompt.builder()
			.text("A treasure chest sits alone in an empty room.")
			.question("mimic",
					YesNoQuestion.builder()
						.text("Is the chest a mimic?")
						.whenYes("It has teeth, or it just moved")
						.whenNo("It is an honest piece of furniture")
						.build())
			.question("trap", YesNoQuestion.of("Is the room trapped?"))
			.question("spell",
					ChoiceQuestion.builder()
						.text("Which spell should the wizard cast?")
						.option("detect_magic", "Reveals magical auras")
						.option("fireball")
						.build())
			.question("danger",
					ScoreQuestion.builder()
						.text("How dangerous is this place?")
						.level("tavern", "The worst threat is the ale")
						.level("dungeon")
						.level("dragon lair")
						.build())
			.build();

		String json = toJson(SystemOneDecisionMapper.questions(prompt));

		JsonAssertions.assertThatJson(json).isEqualTo("""
				{"mimic": {"type": "noul", "instructions": "Is the chest a mimic?",
				"criteria": {"true": "It has teeth, or it just moved", "false": "It is an honest piece of furniture"}},
				"trap": {"type": "noul", "instructions": "Is the room trapped?"},
				"spell": {"type": "choice", "instructions": "Which spell should the wizard cast?",
				"criteria": {"detect_magic": "Reveals magical auras", "fireball": null}},
				"danger": {"type": "score", "instructions": "How dangerous is this place?",
				"criteria": [{"label": "tavern", "description": "The worst threat is the ale"}, "dungeon",
				"dragon lair"]}}""");
	}

	@Test
	void responseCarriesAnswersAndMetadata() {
		DecisionPrompt prompt = DecisionPrompt.builder()
			.text("A treasure chest sits alone in an empty room.")
			.question("mimic", YesNoQuestion.of("Is the chest a mimic?"))
			.question("spell",
					ChoiceQuestion.builder()
						.text("Which spell should the wizard cast?")
						.option("detect_magic")
						.option("fireball")
						.build())
			.question("danger", DANGER)
			.build();
		Map<String, org.springaicommunity.typesafe.response.Answer> answers = new LinkedHashMap<>();
		answers.put("mimic", new NoulAnswer(0.95));
		answers.put("spell", new ChoiceAnswer("detect_magic", Map.of("detect_magic", 0.75, "fireball", 0.25), 0.7));
		answers.put("danger", new ScoreAnswer(0.8, Map.of(), Map.of(0, 0.3, 1, 0.6, 2, 0.1), 0.4));

		DecisionResponse response = SystemOneDecisionMapper.response(prompt,
				new SystemOneResponse(MODEL, answers, new Usage(20, 1), null));

		assertThat(response.getYesNo("mimic").getProbability()).isEqualTo(0.95);
		assertThat(response.getYesNo("mimic").isYes()).isTrue();
		assertThat(response.getChoice("spell").getValue()).isEqualTo("detect_magic");
		assertThat(response.getChoice("spell").getProbabilities()).containsEntry("detect_magic", 0.75)
			.containsEntry("fireball", 0.25);
		assertThat(response.getChoice("spell").getConfidence()).isEqualTo(0.7);
		assertThat(response.getScore("danger").getValue()).isEqualTo(0.8);
		assertThat(response.getScore("danger").getProbabilities()).containsExactly(0.3, 0.6, 0.1);
		assertThat(response.getScore("danger").getNearestLevel()).isEqualTo(1);
		assertThat(response.getScore("danger").getConfidence()).isEqualTo(0.4);
		assertThat(response.getMetadata().getId()).isNull();
		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(20);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(1);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(21);
	}

	@Test
	void scoreProbabilitiesArePaddedToTheQuestionLevels() {
		DecisionResponse response = SystemOneDecisionMapper.response(DANGER_PROMPT, new SystemOneResponse(MODEL,
				Map.of("danger", new ScoreAnswer(1.7, Map.of(), Map.of(0, 0.15, 2, 0.85), 0.5)), Usage.EMPTY));

		assertThat(response.getScore("danger").getProbabilities()).containsExactly(0.15, 0.0, 0.85);
	}

	@Test
	void scoreWithoutProbabilitiesStaysEmpty() {
		DecisionResponse response = SystemOneDecisionMapper.response(DANGER_PROMPT, new SystemOneResponse(MODEL,
				Map.of("danger", new ScoreAnswer(1.2, Map.of(), Map.of(), 0.6)), Usage.EMPTY));

		assertThat(response.getScore("danger").getProbabilities()).isEmpty();
		assertThat(response.getScore("danger").getNearestLevel()).isEqualTo(1);
	}

	@Test
	void failsOnScoreProbabilityOutsideTheLevels() {
		var response = new SystemOneResponse(MODEL,
				Map.of("danger", new ScoreAnswer(2, Map.of(), Map.of(1, 0.1, 9, 0.9), 0.5)), Usage.EMPTY);

		assertThatThrownBy(() -> SystemOneDecisionMapper.response(DANGER_PROMPT, response))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("level 9 of 3");
	}

	@Test
	void failsOnUnknownAnswerType() {
		var response = new SystemOneResponse(MODEL,
				Map.of("danger", new UnknownAnswer("riddle", Map.of("riddle", "What has a beard but no chin?"))),
				Usage.EMPTY);

		assertThatThrownBy(() -> SystemOneDecisionMapper.response(DANGER_PROMPT, response))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Unsupported answer type for question 'danger'");
	}

	@Test
	void failsOnMissingAnswer() {
		DecisionPrompt prompt = DecisionPrompt.builder()
			.text("A treasure chest sits alone in an empty room.")
			.question("mimic", YesNoQuestion.of("Is the chest a mimic?"))
			.question("trap", YesNoQuestion.of("Is the room trapped?"))
			.build();
		var response = new SystemOneResponse(MODEL, Map.of("mimic", new NoulAnswer(0.5)), Usage.EMPTY);

		assertThatThrownBy(() -> SystemOneDecisionMapper.response(prompt, response))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("missing answer for question 'trap'");
	}

	private static String toJson(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

}
