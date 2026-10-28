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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.model.ModelRequest;
import org.springframework.util.Assert;

/**
 * The request to a {@link DecisionModel}: an input, the named questions to answer about
 * it, and optional {@link DecisionOptions}. The questions keep the order they were added
 * in, and the answers come back in the same order.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class DecisionPrompt implements ModelRequest<DecisionInput> {

	private final DecisionInput input;

	private final Map<String, DecisionQuestion> questions;

	private final @Nullable DecisionOptions options;

	private DecisionPrompt(DecisionInput input, Map<String, DecisionQuestion> questions,
			@Nullable DecisionOptions options) {
		Assert.notNull(input, "input cannot be null");
		Assert.notEmpty(questions, "questions cannot be null or empty");
		this.input = input;
		this.questions = Collections.unmodifiableMap(new LinkedHashMap<>(questions));
		this.options = options;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder mutate() {
		return new Builder(this);
	}

	/**
	 * The input to decide about.
	 */
	@Override
	public DecisionInput getInstructions() {
		return this.input;
	}

	/**
	 * The questions to answer, keyed by name, in the order they were added.
	 */
	public Map<String, DecisionQuestion> getQuestions() {
		return this.questions;
	}

	@Override
	public @Nullable DecisionOptions getOptions() {
		return this.options;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DecisionPrompt that)) {
			return false;
		}
		return this.input.equals(that.input) && this.questions.equals(that.questions)
				&& Objects.equals(this.options, that.options);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.input, this.questions, this.options);
	}

	@Override
	public String toString() {
		return "DecisionPrompt{input=" + this.input + ", questions=" + this.questions.keySet() + ", options="
				+ this.options + "}";
	}

	public static final class Builder {

		private @Nullable DecisionInput input;

		private final Map<String, DecisionQuestion> questions = new LinkedHashMap<>();

		private @Nullable DecisionOptions options;

		private Builder() {
		}

		private Builder(DecisionPrompt prompt) {
			this.input = prompt.input;
			this.questions.putAll(prompt.questions);
			this.options = prompt.options;
		}

		public Builder input(DecisionInput input) {
			this.input = input;
			return this;
		}

		public Builder text(String text) {
			return input(DecisionInput.text(text));
		}

		public Builder content(List<? extends MessagePart> parts) {
			return input(DecisionInput.content(parts));
		}

		public Builder content(MessagePart... parts) {
			return input(DecisionInput.content(parts));
		}

		public Builder fields(Map<String, @Nullable Object> fields) {
			return input(DecisionInput.fields(fields));
		}

		public Builder conversation(List<? extends Message> messages) {
			return input(DecisionInput.conversation(messages));
		}

		/**
		 * Add a question. Its name must be unique within the prompt.
		 */
		public Builder question(String name, DecisionQuestion question) {
			Assert.hasText(name, "question name cannot be null or empty");
			Assert.notNull(question, "question cannot be null");
			Assert.isTrue(!this.questions.containsKey(name), () -> "duplicate question '" + name + "'");
			this.questions.put(name, question);
			return this;
		}

		public Builder questions(Map<String, ? extends DecisionQuestion> questions) {
			questions.forEach(this::question);
			return this;
		}

		public Builder options(@Nullable DecisionOptions options) {
			this.options = options;
			return this;
		}

		public DecisionPrompt build() {
			Assert.state(this.input != null, "input cannot be null");
			Assert.state(!this.questions.isEmpty(), "at least one question is required");
			return new DecisionPrompt(this.input, this.questions, this.options);
		}

	}

}
