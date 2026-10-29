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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.ConversationInput;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.input.FieldsInput;
import org.springframework.ai.decision.input.TextInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreLevel;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.Assert;

/**
 * Maps a {@link DecisionPrompt} to a System One request and the response back to a
 * {@link DecisionResponse}.
 * <p>
 * The input becomes the request state: a text as a string, fields as a JSON object in
 * field order, a conversation as {@code {"messages": [{"role", "content", ...}]}} with
 * tool calls and tool results as structured entries. System One decides about text, so
 * media is rejected. Reasoning and unknown parts of a conversation message are not sent,
 * since they are not the content under judgment; in a text or fields input they are
 * rejected. A {@link YesNoQuestion} becomes a {@code noul} with its criteria, a
 * {@link ChoiceQuestion} a {@code choice} and a {@link ScoreQuestion} a {@code score}
 * whose levels carry their description as a structured criterion.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SystemOneDecisionMapper {

	static final String MESSAGES_FIELD = "messages";

	private static final JsonHelper JSON = new JsonHelper();

	private SystemOneDecisionMapper() {
	}

	// Request

	public static JsonContent state(DecisionInput input) {
		if (input instanceof TextInput textInput) {
			return JsonContent.of(text(textInput.getParts(), "the input"));
		}
		if (input instanceof FieldsInput fieldsInput) {
			Map<String, @Nullable Object> state = new LinkedHashMap<>();
			fieldsInput.getFields().forEach((name, value) -> state.put(name, fieldValue(name, value)));
			return JsonContent.of(state);
		}
		if (input instanceof ConversationInput conversationInput) {
			List<Map<String, Object>> messages = new ArrayList<>();
			conversationInput.getMessages().forEach(message -> messages.add(message(message)));
			return JsonContent.object(MESSAGES_FIELD, messages);
		}
		// Unreachable while DecisionInput is sealed to the three kinds above.
		throw new IllegalStateException("Unexpected decision input of type " + input.getClass().getName());
	}

	private static @Nullable Object fieldValue(String name, @Nullable Object value) {
		if (value instanceof MessagePart part) {
			return text(List.of(part), "field '" + name + "'");
		}
		if (value instanceof List<?> list && FieldsInput.isPartList(list)) {
			return text(list.stream().map(MessagePart.class::cast).toList(), "field '" + name + "'");
		}
		return value;
	}

	/**
	 * The text of the given parts. Media is rejected, since System One decides about
	 * text, and so is any other part, which has no place in a plain input.
	 */
	private static String text(List<? extends MessagePart> parts, String what) {
		StringBuilder text = new StringBuilder();
		for (MessagePart part : parts) {
			if (part instanceof TextPart textPart) {
				text.append(textPart.text());
			}
			else if (part instanceof MediaPart) {
				throw new IllegalArgumentException(
						"System One decides about text: " + what + " carries media, which is not supported");
			}
			else {
				throw new IllegalArgumentException("Unsupported decision input part " + part.getClass().getSimpleName()
						+ " in " + what + ": System One accepts text parts");
			}
		}
		return text.toString();
	}

	/**
	 * A message as a JSON object: its role, its text as {@code content}, its tool calls
	 * and its tool results as structured entries. Reasoning and unknown parts are not
	 * sent, media is rejected.
	 */
	private static Map<String, Object> message(Message message) {
		String role = message.getMessageType().getValue();
		StringBuilder content = new StringBuilder();
		List<Map<String, @Nullable Object>> toolCalls = new ArrayList<>();
		List<Map<String, @Nullable Object>> toolResults = new ArrayList<>();
		for (MessagePart part : parts(message)) {
			if (part instanceof TextPart textPart) {
				content.append(textPart.text());
			}
			else if (part instanceof MediaPart) {
				throw new IllegalArgumentException(
						"System One decides about text: a " + role + " message carries media, which is not supported");
			}
			else if (part instanceof ToolCallPart toolCallPart) {
				Map<String, @Nullable Object> call = new LinkedHashMap<>();
				call.put("id", toolCallPart.toolCall().id());
				call.put("name", toolCallPart.toolCall().name());
				call.put("arguments", arguments(toolCallPart.toolCall().arguments()));
				toolCalls.add(call);
			}
			else if (part instanceof ToolResultPart toolResultPart) {
				Map<String, @Nullable Object> result = new LinkedHashMap<>();
				result.put("id", toolResultPart.id());
				result.put("name", toolResultPart.name());
				result.put("result", text(toolResultPart.content(), "a tool result"));
				if (toolResultPart.isError()) {
					result.put("error", true);
				}
				toolResults.add(result);
			}
		}
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("role", role);
		if (!content.isEmpty() || (toolCalls.isEmpty() && toolResults.isEmpty())) {
			entry.put("content", content.toString());
		}
		if (!toolCalls.isEmpty()) {
			entry.put("tool_calls", toolCalls);
		}
		if (!toolResults.isEmpty()) {
			entry.put("tool_results", toolResults);
		}
		return entry;
	}

	/**
	 * The arguments as a JSON object when they are one, so that the state nests them
	 * instead of quoting a JSON string; otherwise as they are.
	 */
	private static @Nullable Object arguments(@Nullable String arguments) {
		if (arguments == null || !arguments.trim().startsWith("{")) {
			return arguments;
		}
		try {
			return JSON.fromJsonToMap(arguments);
		}
		catch (RuntimeException ex) {
			return arguments;
		}
	}

	private static List<MessagePart> parts(Message message) {
		if (message instanceof AbstractMessage abstractMessage) {
			return abstractMessage.getParts();
		}
		return message.getText() != null ? List.of(TextPart.of(message.getText())) : List.of();
	}

	public static Map<String, Question> questions(DecisionPrompt prompt) {
		Map<String, Question> questions = new LinkedHashMap<>();
		prompt.getQuestions().forEach((name, question) -> questions.put(name, question(name, question)));
		return questions;
	}

	static Question question(String name, DecisionQuestion question) {
		if (question instanceof YesNoQuestion yesNo) {
			Noul.Builder builder = Noul.builder().instructions(yesNo.getText());
			if (yesNo.getWhenYes() != null) {
				builder.whenTrue(yesNo.getWhenYes());
			}
			if (yesNo.getWhenNo() != null) {
				builder.whenFalse(yesNo.getWhenNo());
			}
			return builder.build();
		}
		if (question instanceof ChoiceQuestion choice) {
			Choice.Builder builder = Choice.builder().instructions(choice.getText());
			choice.getOptions().forEach((option, description) -> {
				if (description != null) {
					builder.option(option, description);
				}
				else {
					builder.option(option);
				}
			});
			return builder.build();
		}
		if (question instanceof ScoreQuestion score) {
			Score.Builder builder = Score.builder().instructions(score.getText());
			for (ScoreLevel level : score.getLevels()) {
				if (level.getDescription() != null) {
					builder.level(JsonContent.object("label", level.getLabel(), "description", level.getDescription()));
				}
				else {
					builder.level(level.getLabel());
				}
			}
			return builder.build();
		}
		// Unreachable while DecisionQuestion is sealed to the three kinds above.
		throw new IllegalStateException(
				"Unexpected decision question '" + name + "' of type " + question.getClass().getName());
	}

	// Response

	public static DecisionResponse response(DecisionPrompt prompt, SystemOneResponse response) {
		DecisionResponse.Builder builder = DecisionResponse.builder(prompt)
			.metadata(DecisionResponseMetadata.builder()
				.id(response.requestId())
				.model(response.model())
				.usage(usage(response.usage()))
				.build());
		response.answers()
			.forEach((name, answer) -> builder.answer(name, answer(name, answer, prompt.getQuestions().get(name))));
		return builder.build();
	}

	/**
	 * The number of levels the reported probabilities imply, for an answer the prompt has
	 * no question for.
	 */
	private static int reportedLevels(org.springaicommunity.typesafe.response.ScoreAnswer score) {
		return score.probabilities().keySet().stream().mapToInt(level -> level + 1).max().orElse(0);
	}

	private static DefaultUsage usage(Usage usage) {
		return new DefaultUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usage);
	}

	/**
	 * The answer as the Spring AI type matching its kind. The SDK reports a confidence of
	 * {@code 0} when the server sends none, so a zero confidence may mean an absent one.
	 */
	private static DecisionAnswer answer(String name, Answer answer, @Nullable DecisionQuestion question) {
		if (answer instanceof NoulAnswer noul) {
			return YesNoAnswer.builder().probability(noul.value()).build();
		}
		if (answer instanceof org.springaicommunity.typesafe.response.ChoiceAnswer choice) {
			return ChoiceAnswer.builder()
				.value(choice.value())
				.probabilities(choice.probabilities())
				.confidence(choice.confidence())
				.build();
		}
		if (answer instanceof org.springaicommunity.typesafe.response.ScoreAnswer score) {
			int levels = question instanceof ScoreQuestion scoreQuestion ? scoreQuestion.getLevels().size()
					: Math.max(score.maxLevel() + 1, reportedLevels(score));
			// An empty report stays empty, as the ScoreAnswer contract promises.
			List<Double> probabilities = new ArrayList<>();
			if (!score.probabilities().isEmpty()) {
				probabilities.addAll(Collections.nCopies(levels, 0.0));
				score.probabilities().forEach((level, probability) -> {
					Assert.state(level >= 0 && level < levels, () -> "The answer to question '" + name
							+ "' reports a probability for level " + level + " of " + levels);
					probabilities.set(level, probability);
				});
			}
			return ScoreAnswer.builder()
				.value(score.value())
				.probabilities(probabilities)
				.confidence(score.confidence())
				.build();
		}
		throw new IllegalStateException("Unsupported answer type for question '" + name + "': " + answer);
	}

}
