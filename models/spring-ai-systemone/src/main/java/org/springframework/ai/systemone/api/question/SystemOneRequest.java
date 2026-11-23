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

package org.springframework.ai.systemone.api.question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.util.Assert;

/**
 * The body of a {@code POST /v1/systemone} call: one state, one model and a map of named
 * questions that are all answered against that state in parallel.
 *
 * @param state the content to evaluate; text, a JSON object or a JSON array
 * @param model the model name or alias, for example {@code jev-latest}; {@code null} to
 * let {@code SystemOneClient} supply its default. A request is never sent without one.
 * @param questions the questions, keyed by the name their answers will carry
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemOneRequest(@JsonProperty("state") JsonContent state, @JsonProperty("model") @Nullable String model,
		@JsonProperty("questions") Map<String, Question> questions) {

	public SystemOneRequest {
		Assert.notNull(state, "state must not be null");
		Assert.notEmpty(questions, "questions must declare at least one question");
		questions = Collections.unmodifiableMap(new LinkedHashMap<>(questions));
	}

	/**
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a copy of this request using the given model. Used by the client to apply
	 * its default model to a request that did not name one.
	 * @param model the model name or alias
	 * @return a new request
	 */
	public SystemOneRequest withModel(String model) {
		return new SystemOneRequest(this.state, model, this.questions);
	}

	/**
	 * Builder for {@link SystemOneRequest}.
	 */
	public static final class Builder {

		private @Nullable JsonContent state;

		private @Nullable String model;

		private final Map<String, Question> questions = new LinkedHashMap<>();

		private Builder() {
		}

		public Builder state(String state) {
			Assert.notNull(state, "state must not be null");
			return state(JsonContent.of(state));
		}

		/**
		 * Sets a structured state. Its fields are sent in the map's iteration order,
		 * which can change an answer: pass an ordered map, or
		 * {@link JsonContent#object(Object...)} to {@link #state(JsonContent)}, not
		 * {@code Map.of}, whose order changes with every JVM run.
		 * @param state the state
		 * @return this builder
		 */
		public Builder state(Map<String, ?> state) {
			Assert.notEmpty(state, "state must not be empty");
			return state(JsonContent.of(state));
		}

		public Builder state(List<?> state) {
			Assert.notEmpty(state, "state must not be empty");
			return state(JsonContent.of(state));
		}

		public Builder state(JsonContent state) {
			Assert.notNull(state, "state must not be null");
			this.state = state;
			return this;
		}

		public Builder model(String model) {
			Assert.hasText(model, "model must not be empty");
			this.model = model;
			return this;
		}

		/**
		 * Adds one named question.
		 * @param name the name the answer will carry in the response
		 * @param question the question
		 * @return this builder
		 */
		public Builder question(String name, Question question) {
			Assert.hasText(name, "question name must not be empty");
			Assert.notNull(question, "question must not be null");
			this.questions.put(name, question);
			return this;
		}

		/**
		 * Adds several named questions, in the map's iteration order. Prefer
		 * {@link #question(String, Question)} or a {@code LinkedHashMap}: {@code Map.of}
		 * iterates in an order that changes with every JVM run.
		 * @param questions the questions, keyed by name
		 * @return this builder
		 */
		public Builder questions(Map<String, ? extends Question> questions) {
			Assert.notEmpty(questions, "questions must not be empty");
			questions.forEach(this::question);
			return this;
		}

		public SystemOneRequest build() {
			Assert.notNull(this.state, "state must be set");
			return new SystemOneRequest(this.state, this.model, this.questions);
		}

	}
}
