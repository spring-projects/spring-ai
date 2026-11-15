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

package org.springframework.ai.chat.client.advisor.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

/**
 * Reads the texts of a reply that have to pass the output screen: for each choice its
 * answer, and its reasoning when the provider shows it.
 * <p>
 * A reply arrives in more than one shape. A generation is one choice, whose message holds
 * the answer as text parts and the reasoning as {@link ReasoningPart}s. A streamed reply
 * is a list of responses, each holding a piece of a choice. And a model that predates the
 * message parts returns reasoning as a generation of its own, flagged in the message
 * metadata, or one generation per part of a choice, tagged with the choice it belongs to.
 * <p>
 * The answer and the reasoning of a choice are screened apart, and so is each choice, so
 * that a long harmless text cannot dilute a short harmful one.
 *
 * @author Christian Tzolov
 */
final class ReplyTexts {

	private ReplyTexts() {
	}

	/**
	 * The texts of a reply: one per choice, and one per choice that shows its reasoning,
	 * in the order they first appear.
	 * @param reply the response of a call, or the responses of a stream in order
	 * @return the texts, blank ones left out
	 */
	static List<String> of(List<ChatClientResponse> reply) {
		List<Text> texts = new ArrayList<>();
		for (ChatClientResponse response : reply) {
			ChatResponse chatResponse = response.chatResponse();
			if (chatResponse == null) {
				continue;
			}
			List<Generation> generations = chatResponse.getResults();
			for (int position = 0; position < generations.size(); position++) {
				Generation generation = generations.get(position);
				AssistantMessage message = generation.getOutput();
				Object choice = choiceOf(generation, position);
				String reasoning = message.getReasoning()
					.stream()
					.map(ReasoningPart::text)
					.filter(Objects::nonNull)
					.collect(Collectors.joining());
				append(texts, true, choice, reasoning);
				boolean flaggedAsReasoning = isFlaggedAsReasoning(generation);
				// A message flagged as reasoning that also holds reasoning parts repeats
				// them as its text, for callers that predate the parts.
				if (!flaggedAsReasoning || reasoning.isEmpty()) {
					append(texts, flaggedAsReasoning, choice, message.getText());
				}
			}
		}
		return texts.stream().map(text -> text.text().toString()).filter(StringUtils::hasText).toList();
	}

	/** Adds to the text of the same kind and choice, or starts one. */
	private static void append(List<Text> texts, boolean reasoning, Object choice, @Nullable String fragment) {
		if (!StringUtils.hasLength(fragment)) {
			return;
		}
		Text text = texts.stream()
			.filter(candidate -> candidate.reasoning() == reasoning && choice.equals(candidate.choice()))
			.findFirst()
			.orElse(null);
		if (text == null) {
			text = new Text(reasoning, choice, new StringBuilder());
			texts.add(text);
		}
		text.text().append(fragment);
	}

	/**
	 * Whether the message metadata flags the generation as reasoning, as models did
	 * before reasoning became a message part: {@code signature}, {@code data} (redacted)
	 * and {@code thinking} (streamed) for Anthropic, {@code isThought} for Google GenAI.
	 */
	private static boolean isFlaggedAsReasoning(Generation generation) {
		Map<String, Object> properties = generation.getOutput().getMetadata();
		return properties.containsKey("signature") || properties.containsKey("data")
				|| Boolean.TRUE.equals(properties.get("thinking")) || Boolean.TRUE.equals(properties.get("isThought"));
	}

	/**
	 * The choice a generation belongs to: the index the provider tags it with, or else
	 * its position in its response. In one response the positions tell the choices apart;
	 * across the responses of a stream they join the pieces of the same choice.
	 */
	private static Object choiceOf(Generation generation, int position) {
		Map<String, Object> properties = generation.getOutput().getMetadata();
		Object index = properties.containsKey("candidateIndex") ? properties.get("candidateIndex")
				: properties.get("index");
		return (index != null) ? index : "position-" + position;
	}

	private record Text(boolean reasoning, Object choice, StringBuilder text) {
	}

}
