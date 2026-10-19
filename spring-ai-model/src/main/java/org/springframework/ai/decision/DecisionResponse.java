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

package org.springframework.ai.decision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.model.ModelResponse;
import org.springframework.util.Assert;

/**
 * The response of a {@link DecisionModel}: one {@link DecisionResult} per question, in
 * the order the questions were asked, and the response metadata.
 * <p>
 * A response is built with {@link #builder(DecisionPrompt)}, which orders the answers by
 * the prompt's questions and checks that each one answers its question, so a response
 * always matches the prompt it answers.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class DecisionResponse implements ModelResponse<DecisionResult> {

	private final List<DecisionResult> results;

	private final Map<String, DecisionAnswer> answers;

	private final DecisionResponseMetadata metadata;

	private DecisionResponse(List<DecisionResult> results, DecisionResponseMetadata metadata) {
		this.results = List.copyOf(results);
		Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
		for (DecisionResult result : this.results) {
			answers.put(result.getName(), result.getOutput());
		}
		this.answers = Collections.unmodifiableMap(answers);
		this.metadata = metadata;
	}

	/**
	 * A builder for the response to the given prompt. {@link Builder#build()} orders the
	 * answers by the prompt's questions and checks that each question is answered by an
	 * answer of the matching type.
	 */
	public static Builder builder(DecisionPrompt prompt) {
		return new Builder(prompt);
	}

	@Override
	public List<DecisionResult> getResults() {
		return this.results;
	}

	/**
	 * The first result, or {@code null} when there are none.
	 */
	@Override
	public @Nullable DecisionResult getResult() {
		return this.results.isEmpty() ? null : this.results.get(0);
	}

	@Override
	public DecisionResponseMetadata getMetadata() {
		return this.metadata;
	}

	/**
	 * The answers, keyed by question name, in the order the questions were asked.
	 */
	public Map<String, DecisionAnswer> getAnswers() {
		return this.answers;
	}

	/**
	 * Whether the question was answered, including by a refusal.
	 */
	public boolean hasAnswer(String name) {
		return this.answers.containsKey(name);
	}

	/**
	 * The answer to the given question.
	 */
	public DecisionAnswer getAnswer(String name) {
		DecisionAnswer answer = this.answers.get(name);
		Assert.isTrue(answer != null, () -> "no answer for question '" + name + "'");
		return answer;
	}

	/**
	 * The answer to the given question, as the given type. Fails with an
	 * {@link IllegalStateException} when the provider refused to answer.
	 */
	public <A extends DecisionAnswer> A getAnswer(String name, Class<A> type) {
		DecisionAnswer answer = getAnswer(name);
		if (type.isInstance(answer)) {
			return type.cast(answer);
		}
		if (answer instanceof RefusalAnswer) {
			throw new IllegalStateException("the provider refused to answer question '" + name + "'");
		}
		throw new IllegalArgumentException("the answer to question '" + name + "' is a "
				+ answer.getClass().getSimpleName() + ", not a " + type.getSimpleName());
	}

	public YesNoAnswer getYesNo(String name) {
		return getAnswer(name, YesNoAnswer.class);
	}

	public ChoiceAnswer getChoice(String name) {
		return getAnswer(name, ChoiceAnswer.class);
	}

	public ScoreAnswer getScore(String name) {
		return getAnswer(name, ScoreAnswer.class);
	}

	/**
	 * Whether the provider refused to answer the given question.
	 */
	public boolean isRefused(String name) {
		return getAnswer(name) instanceof RefusalAnswer;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DecisionResponse that)) {
			return false;
		}
		return this.results.equals(that.results) && Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.results, this.metadata);
	}

	@Override
	public String toString() {
		return "DecisionResponse{results=" + this.results + ", metadata=" + this.metadata + "}";
	}

	/**
	 * Builds a {@link DecisionResponse} that answers a {@link DecisionPrompt}.
	 */
	public static final class Builder {

		private final DecisionPrompt prompt;

		private final Map<String, DecisionAnswer> answers = new LinkedHashMap<>();

		private final Map<String, DecisionResultMetadata> resultMetadata = new LinkedHashMap<>();

		private DecisionResponseMetadata metadata = DecisionResponseMetadata.builder().build();

		private Builder(DecisionPrompt prompt) {
			Assert.notNull(prompt, "prompt cannot be null");
			this.prompt = prompt;
		}

		/**
		 * Add the answer to a question.
		 */
		public Builder answer(String name, DecisionAnswer answer) {
			return answer(name, answer, DecisionResult.NO_METADATA);
		}

		/**
		 * Add the answer to a question, with metadata about that answer.
		 */
		public Builder answer(String name, DecisionAnswer answer, DecisionResultMetadata metadata) {
			Assert.hasText(name, "name cannot be null or empty");
			Assert.notNull(answer, "answer cannot be null");
			Assert.notNull(metadata, "metadata cannot be null");
			Assert.isTrue(!this.answers.containsKey(name), () -> "duplicate answer for question '" + name + "'");
			this.answers.put(name, answer);
			this.resultMetadata.put(name, metadata);
			return this;
		}

		public Builder answers(Map<String, ? extends DecisionAnswer> answers) {
			answers.forEach(this::answer);
			return this;
		}

		public Builder metadata(DecisionResponseMetadata metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			this.metadata = metadata;
			return this;
		}

		/**
		 * Build the response, with the results in the order of the prompt's questions.
		 * Fails when a question has no answer, an answer has no question, or an answer
		 * doesn't match its question: a wrong type, a choice outside the offered options,
		 * or a score outside the levels. A refusal is accepted for any question, and any
		 * answer for a question type other than the built-in ones.
		 */
		public DecisionResponse build() {
			for (String name : this.answers.keySet()) {
				Assert.state(this.prompt.getQuestions().containsKey(name),
						() -> "the prompt has no question named '" + name + "'");
			}
			List<DecisionResult> results = new ArrayList<>();
			for (Map.Entry<String, DecisionQuestion> entry : this.prompt.getQuestions().entrySet()) {
				String name = entry.getKey();
				DecisionAnswer answer = this.answers.get(name);
				Assert.state(answer != null, () -> "missing answer for question '" + name + "'");
				check(name, entry.getValue(), answer);
				results.add(new DecisionResult(name, answer,
						this.resultMetadata.getOrDefault(name, DecisionResult.NO_METADATA)));
			}
			return new DecisionResponse(results, this.metadata);
		}

		private static void check(String name, DecisionQuestion question, DecisionAnswer answer) {
			if (answer instanceof RefusalAnswer) {
				return;
			}
			if (question instanceof YesNoQuestion) {
				expect(name, answer, YesNoAnswer.class);
			}
			else if (question instanceof ChoiceQuestion choiceQuestion) {
				ChoiceAnswer choiceAnswer = expect(name, answer, ChoiceAnswer.class);
				Assert.state(choiceQuestion.getOptions().containsKey(choiceAnswer.getValue()),
						() -> "the answer to question '" + name + "' is '" + choiceAnswer.getValue()
								+ "', which is not one of the offered options " + choiceQuestion.getOptionNames());
				for (String option : choiceAnswer.getProbabilities().keySet()) {
					Assert.state(choiceQuestion.getOptions().containsKey(option),
							() -> "the answer to question '" + name + "' reports a probability for '" + option
									+ "', which is not one of the offered options " + choiceQuestion.getOptionNames());
				}
			}
			else if (question instanceof ScoreQuestion scoreQuestion) {
				ScoreAnswer scoreAnswer = expect(name, answer, ScoreAnswer.class);
				int maxLevel = scoreQuestion.getLevels().size() - 1;
				Assert.state(scoreAnswer.getValue() <= maxLevel, () -> "the answer to question '" + name + "' is "
						+ scoreAnswer.getValue() + ", which is above the highest level " + maxLevel);
				Assert.state(
						scoreAnswer.getProbabilities().isEmpty()
								|| scoreAnswer.getProbabilities().size() == scoreQuestion.getLevels().size(),
						() -> "the answer to question '" + name + "' reports " + scoreAnswer.getProbabilities().size()
								+ " probabilities for " + scoreQuestion.getLevels().size() + " levels");
			}
		}

		private static <A extends DecisionAnswer> A expect(String name, DecisionAnswer answer, Class<A> type) {
			Assert.state(type.isInstance(answer), () -> "the answer to question '" + name + "' is a "
					+ answer.getClass().getSimpleName() + ", but a " + type.getSimpleName() + " was expected");
			return type.cast(answer);
		}

	}

}
