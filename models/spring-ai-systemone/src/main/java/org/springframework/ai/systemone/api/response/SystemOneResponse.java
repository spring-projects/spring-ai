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

package org.springframework.ai.systemone.api.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

import org.springframework.ai.systemone.api.exception.SystemOneAnswerTypeException;
import org.springframework.ai.systemone.api.exception.SystemOneMissingAnswerException;
import org.springframework.util.Assert;

/**
 * The body of a {@code POST /v1/systemone} response: one answer per question, plus the
 * model that produced them and the token usage.
 *
 * @param model the model that answered
 * @param answers the answers, keyed by the names the request gave its questions
 * @param usage the token consumption of the call
 * @param requestId the {@code x-typesafe-request-id} response header, carried here rather
 * than on the wire so it can be quoted in a support request
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemOneResponse(//
		@JsonProperty("model") String model, //
		@JsonProperty("answers") Map<String, Answer> answers, //
		@JsonProperty("usage") Usage usage, //
		@JsonIgnore @Nullable String requestId) {

	public SystemOneResponse {
		// An ordered copy, so the answers iterate in the order the request named them.
		answers = answers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
		usage = usage == null ? Usage.EMPTY : usage;
	}

	/**
	 * Jackson entry point. The request id is not part of the body, so it starts out
	 * {@code null} and is filled in from the response headers by the client.
	 * @param model the model that answered
	 * @param answers the answers
	 * @param usage the token consumption
	 */
	@JsonCreator
	public SystemOneResponse(@JsonProperty("model") String model,
			@JsonProperty("answers") @JsonDeserialize(
					contentUsing = AnswerDeserializer.class) Map<String, Answer> answers,
			@JsonProperty("usage") @Nullable Usage usage) {
		this(model, answers, (usage != null) ? usage : Usage.EMPTY, null);
	}

	/**
	 * Returns a copy of this response carrying the given request id.
	 * @param requestId the {@code x-typesafe-request-id} header value
	 * @return a new response
	 */
	public SystemOneResponse withRequestId(@Nullable String requestId) {
		return new SystemOneResponse(this.model, this.answers, this.usage, requestId);
	}

	/**
	 * Returns the answer to a question.
	 * @param name the question name
	 * @return the answer, never {@code null}
	 * @throws SystemOneMissingAnswerException when no answer carries that name
	 */
	public Answer answer(String name) {
		Assert.hasText(name, "name must not be empty");
		Answer answer = this.answers.get(name);
		if (answer == null) {
			throw new SystemOneMissingAnswerException(name, this.answers.keySet());
		}
		return answer;
	}

	/**
	 * Returns the answer to a noul question.
	 * @param name the question name
	 * @return the noul answer
	 * @throws SystemOneMissingAnswerException when no answer carries that name
	 * @throws SystemOneAnswerTypeException when the answer is of another kind
	 */
	public NoulAnswer noul(String name) {
		return as(name, NoulAnswer.class, AnswerType.NOUL);
	}

	/**
	 * Shorthand for {@code noul(name).value()}.
	 * @param name the question name
	 * @return the truth value between {@code 0} and {@code 1}
	 */
	public double noulValue(String name) {
		return noul(name).value();
	}

	/**
	 * Returns the answer to a choice question.
	 * @param name the question name
	 * @return the choice answer
	 * @throws SystemOneMissingAnswerException when no answer carries that name
	 * @throws SystemOneAnswerTypeException when the answer is of another kind
	 */
	public ChoiceAnswer choice(String name) {
		return as(name, ChoiceAnswer.class, AnswerType.CHOICE);
	}

	/**
	 * Shorthand for {@code choice(name).value()}.
	 * @param name the question name
	 * @return the selected option label
	 */
	public String choiceValue(String name) {
		return choice(name).value();
	}

	/**
	 * Returns the answer to a score question.
	 * @param name the question name
	 * @return the score answer
	 * @throws SystemOneMissingAnswerException when no answer carries that name
	 * @throws SystemOneAnswerTypeException when the answer is of another kind
	 */
	public ScoreAnswer score(String name) {
		return as(name, ScoreAnswer.class, AnswerType.SCORE);
	}

	/**
	 * Shorthand for {@code score(name).value()}.
	 * @param name the question name
	 * @return the probability-weighted score
	 */
	public double scoreValue(String name) {
		return score(name).value();
	}

	/**
	 * @return only the noul answers, keyed by question name
	 */
	public Map<String, NoulAnswer> nouls() {
		return filter(NoulAnswer.class);
	}

	/**
	 * @return only the choice answers, keyed by question name
	 */
	public Map<String, ChoiceAnswer> choices() {
		return filter(ChoiceAnswer.class);
	}

	/**
	 * @return only the score answers, keyed by question name
	 */
	public Map<String, ScoreAnswer> scores() {
		return filter(ScoreAnswer.class);
	}

	private <T extends Answer> T as(String name, Class<T> type, AnswerType expected) {
		Answer answer = answer(name);
		if (!type.isInstance(answer)) {
			throw new SystemOneAnswerTypeException(name, expected, answer.type());
		}
		return type.cast(answer);
	}

	private <T extends Answer> Map<String, T> filter(Class<T> type) {
		Predicate<Map.Entry<String, Answer>> isType = entry -> type.isInstance(entry.getValue());
		return this.answers.entrySet()
			.stream()
			.filter(isType)
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> type.cast(entry.getValue()), (a, b) -> a,
					LinkedHashMap::new));
	}
}
