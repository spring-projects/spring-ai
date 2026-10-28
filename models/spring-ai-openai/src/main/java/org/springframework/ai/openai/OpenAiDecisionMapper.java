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

package org.springframework.ai.openai;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionChoiceValue;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import com.openai.models.decisions.DecisionInputText;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
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
import org.springframework.util.StringUtils;

/**
 * Maps a {@link DecisionPrompt} to the OpenAI Decisions API request, and the API response
 * back to a {@link DecisionResponse}.
 *
 * <p>
 * An input made of text only is sent as a string. Any other input becomes one user
 * message made of parts, in order: the content parts of a {@link TextInput}; a text part
 * per field, or the field name followed by its content parts; or, for a conversation, the
 * parts of each message, prefixed with its role, with tool calls and results as extra
 * lines and images as image parts where they appear. A {@link YesNoQuestion} becomes a
 * {@code predicate} question, with its criteria added to the instructions since the API
 * has no field for them; a {@link ChoiceQuestion} becomes a {@code choice} question and a
 * {@link ScoreQuestion} a {@code score} question.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
final class OpenAiDecisionMapper {

	private static final JsonHelper JSON = new JsonHelper();

	private OpenAiDecisionMapper() {
	}

	// Request

	static DecisionCreateParams.Input input(DecisionInput input) {
		if (input instanceof TextInput textInput
				&& textInput.getParts().stream().allMatch(TextPart.class::isInstance)) {
			return DecisionCreateParams.Input.ofString(textInput.getText());
		}
		List<DecisionInputPart> parts = new ArrayList<>();
		if (input instanceof TextInput textInput) {
			textInput.getParts().forEach(part -> parts.add(part(part)));
		}
		else if (input instanceof FieldsInput fieldsInput) {
			fieldsInput.getFields().forEach((name, value) -> {
				if (value instanceof MessagePart part) {
					parts.add(text(name + ":"));
					parts.add(part(part));
				}
				else if (FieldsInput.isPartList(value)) {
					parts.add(text(name + ":"));
					((List<?>) value).forEach(part -> parts.add(part((MessagePart) part)));
				}
				else {
					parts.add(text(name + ": " + (value instanceof String string ? string : JSON.toJson(value))));
				}
			});
		}
		else if (input instanceof ConversationInput conversationInput) {
			conversationInput.getMessages().forEach(message -> parts.addAll(messageParts(message)));
		}
		else {
			// Unreachable while DecisionInput is sealed to the three kinds above.
			throw new IllegalStateException("Unexpected decision input of type " + input.getClass().getName());
		}
		return DecisionCreateParams.Input
			.ofDecisionInputMessages(List.of(DecisionInputMessage.builder().contentOfParts(parts).build()));
	}

	/**
	 * The parts of a message, in order: its text, the tool calls it makes and the results
	 * it carries become lines of a text part prefixed with the role, and each image
	 * becomes an image part where it appears. Reasoning and unknown parts are not sent.
	 */
	private static List<DecisionInputPart> messageParts(Message message) {
		String role = message.getMessageType().getValue();
		List<DecisionInputPart> parts = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		for (MessagePart part : parts(message)) {
			if (part instanceof MediaPart mediaPart) {
				flushLines(role, lines, parts);
				parts.add(image(mediaPart.media()));
			}
			else {
				String line = line(part);
				if (line != null) {
					lines.add(line);
				}
			}
		}
		flushLines(role, lines, parts);
		return parts;
	}

	private static List<MessagePart> parts(Message message) {
		if (message instanceof AbstractMessage abstractMessage) {
			return abstractMessage.getParts();
		}
		return message.getText() != null ? List.of(TextPart.of(message.getText())) : List.of();
	}

	/**
	 * Adds the pending lines as one text part. The first part of a message always goes
	 * out, prefixed with the role, so that the model knows who speaks even when the
	 * message starts with an image; later ones only when there is something to say.
	 */
	private static void flushLines(String role, List<String> lines, List<DecisionInputPart> parts) {
		if (parts.isEmpty()) {
			parts.add(text(role + ": " + String.join("\n", lines)));
		}
		else if (!lines.isEmpty()) {
			parts.add(text(String.join("\n", lines)));
		}
		lines.clear();
	}

	private static @Nullable String line(MessagePart part) {
		if (part instanceof TextPart textPart) {
			return StringUtils.hasText(textPart.text()) ? textPart.text() : null;
		}
		if (part instanceof ToolCallPart toolCallPart) {
			return "[calls " + toolCallPart.toolCall().name() + " " + toolCallPart.toolCall().arguments() + "]";
		}
		if (part instanceof ToolResultPart toolResultPart) {
			String result = toolResultPart.content()
				.stream()
				.filter(TextPart.class::isInstance)
				.map(TextPart.class::cast)
				.map(TextPart::text)
				.collect(Collectors.joining());
			return toolResultPart.name() + ": " + result;
		}
		return null;
	}

	private static DecisionInputPart part(MessagePart part) {
		if (part instanceof TextPart textPart) {
			return text(textPart.text());
		}
		if (part instanceof MediaPart mediaPart) {
			return image(mediaPart.media());
		}
		throw new IllegalArgumentException("Unsupported decision input part " + part.getClass().getSimpleName()
				+ ": the OpenAI Decisions API accepts text and image parts");
	}

	private static DecisionInputPart text(String text) {
		return DecisionInputPart.ofInputText(DecisionInputText.builder().text(text).build());
	}

	private static DecisionInputPart image(Media media) {
		Assert.isTrue("image".equals(media.getMimeType().getType()),
				() -> "Only images are supported as decision input media, not " + media.getMimeType());
		return DecisionInputPart.ofInputImage(DecisionInputImage.builder().imageUrl(imageUrl(media)).build());
	}

	/**
	 * The image as a base64 data URL, which is the only form the Decisions API accepts:
	 * hosted URLs are not supported, so the image has to be given as bytes, or as a data
	 * URL already.
	 */
	private static String imageUrl(Media media) {
		Object data = media.getData();
		if (data instanceof byte[] bytes) {
			return "data:" + media.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
		}
		String url = data instanceof URI uri ? uri.toString() : data instanceof String text ? text : null;
		if (url != null && url.startsWith("data:")) {
			return url;
		}
		throw new IllegalArgumentException("The OpenAI Decisions API accepts only inline base64 images, not "
				+ (url != null ? "the URL " + url : data.getClass().getName())
				+ ": give the image as bytes or as a Resource");
	}

	static DecisionCreateParams.Question question(String name, DecisionQuestion question) {
		if (question instanceof YesNoQuestion yesNo) {
			return DecisionCreateParams.Question.ofPredicate(DecisionCreateParams.Question.Predicate.builder()
				.name(name)
				.instructions(predicateInstructions(yesNo))
				.build());
		}
		if (question instanceof ChoiceQuestion choice) {
			var builder = DecisionCreateParams.Question.Choice.builder().name(name).instructions(choice.getText());
			choice.getOptions().forEach((option, description) -> {
				var optionBuilder = DecisionChoiceOption.builder().value(option);
				if (description != null) {
					optionBuilder.description(description);
				}
				builder.addChoice(optionBuilder.build());
			});
			return DecisionCreateParams.Question.ofChoice(builder.build());
		}
		if (question instanceof ScoreQuestion scoreQuestion) {
			var builder = DecisionCreateParams.Question.Score.builder()
				.name(name)
				.instructions(scoreQuestion.getText());
			for (ScoreLevel level : scoreQuestion.getLevels()) {
				var levelBuilder = DecisionCreateParams.Question.Score.Level.builder().label(level.getLabel());
				if (level.getDescription() != null) {
					levelBuilder.description(level.getDescription());
				}
				builder.addLevel(levelBuilder.build());
			}
			return DecisionCreateParams.Question.ofScore(builder.build());
		}
		// Unreachable while DecisionQuestion is sealed to the three kinds above.
		throw new IllegalStateException(
				"Unexpected decision question '" + name + "' of type " + question.getClass().getName());
	}

	/**
	 * The question text followed by the criteria, since a predicate question has no field
	 * for them.
	 */
	private static String predicateInstructions(YesNoQuestion question) {
		StringBuilder instructions = new StringBuilder(question.getText());
		if (question.getWhenYes() != null) {
			instructions.append("\nAnswer \"yes\" if: ").append(question.getWhenYes());
		}
		if (question.getWhenNo() != null) {
			instructions.append("\nAnswer \"no\" if: ").append(question.getWhenNo());
		}
		return instructions.toString();
	}

	// Response

	/**
	 * The response to the prompt, with the given request id, if any, as the id of its
	 * metadata.
	 */
	static DecisionResponse response(DecisionPrompt prompt, Decision decision, @Nullable String requestId) {
		List<String> names = List.copyOf(prompt.getQuestions().keySet());
		DecisionResponse.Builder builder = DecisionResponse.builder(prompt)
			.metadata(DecisionResponseMetadata.builder()
				.id(requestId)
				.model(decision.model())
				.usage(usage(decision.usage()))
				.build());
		List<Decision.Answer> answers = decision.answers();
		for (int i = 0; i < answers.size(); i++) {
			Decision.Answer answer = answers.get(i);
			String name = answerName(answer);
			if (name == null) {
				// Answers come back in question order, so fall back to the position.
				Assert.state(i < names.size(), "The response has more answers than the request has questions");
				name = names.get(i);
			}
			builder.answer(name, answer(answer, prompt.getQuestions().get(name)));
		}
		return builder.build();
	}

	private static @Nullable String answerName(Decision.Answer answer) {
		if (answer.isPredicate()) {
			return answer.asPredicate().name().orElse(null);
		}
		if (answer.isChoice()) {
			return answer.asChoice().name().orElse(null);
		}
		if (answer.isScore()) {
			return answer.asScore().name().orElse(null);
		}
		if (answer.isRefusal()) {
			return answer.asRefusal().name().orElse(null);
		}
		return null;
	}

	private static DecisionAnswer answer(Decision.Answer answer, @Nullable DecisionQuestion question) {
		if (answer.isPredicate()) {
			return YesNoAnswer.builder().probability(answer.asPredicate().probability()).build();
		}
		if (answer.isChoice()) {
			Decision.Answer.Choice choice = answer.asChoice();
			Map<String, Double> probabilities = new LinkedHashMap<>();
			choice.probabilities()
				.forEach(probability -> probabilities.put(choiceValue(probability.value()), probability.probability()));
			return ChoiceAnswer.builder()
				.value(choiceValue(choice.choice()))
				.probabilities(probabilities)
				.confidence(choice.confidence())
				.build();
		}
		if (answer.isScore()) {
			Decision.Answer.Score score = answer.asScore();
			int levels = question instanceof ScoreQuestion scoreQuestion ? scoreQuestion.getLevels().size()
					: score.probabilities().size();
			List<Double> probabilities = new ArrayList<>(Collections.nCopies(levels, 0.0));
			for (Decision.Answer.Score.Probability probability : score.probabilities()) {
				int level = (int) probability.value();
				Assert.state(level >= 0 && level < levels,
						() -> "The response reports a probability for level " + level + " of " + levels);
				probabilities.set(level, probability.probability());
			}
			return ScoreAnswer.builder()
				.value(score.score())
				.probabilities(probabilities)
				.confidence(score.confidence())
				.build();
		}
		if (answer.isRefusal()) {
			return RefusalAnswer.builder().build();
		}
		throw new IllegalStateException("Unsupported answer type in the response: " + answer);
	}

	private static String choiceValue(DecisionChoiceValue value) {
		return value.isBool() ? String.valueOf(value.asBool()) : value.asString();
	}

	private static Usage usage(Decision.Usage usage) {
		return new DefaultUsage((int) usage.inputTokens(), (int) usage.outputTokens(), (int) usage.totalTokens(), usage,
				usage.inputTokensDetails().cachedTokens(), usage.inputTokensDetails().cacheWriteTokens());
	}

}
