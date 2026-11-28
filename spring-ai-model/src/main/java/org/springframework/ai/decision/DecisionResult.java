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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.model.ModelResult;
import org.springframework.util.Assert;

/**
 * The answer to one question of a {@link DecisionPrompt}, named after the question.
 * Results are created by {@link DecisionResponse.Builder}, which puts them in the order
 * of the prompt's questions.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class DecisionResult implements ModelResult<DecisionAnswer> {

	static final DecisionResultMetadata NO_METADATA = new DecisionResultMetadata() {

	};

	private final String name;

	private final DecisionAnswer answer;

	private final DecisionResultMetadata metadata;

	DecisionResult(String name, DecisionAnswer answer, DecisionResultMetadata metadata) {
		Assert.hasText(name, "name cannot be null or empty");
		Assert.notNull(answer, "answer cannot be null");
		Assert.notNull(metadata, "metadata cannot be null");
		this.name = name;
		this.answer = answer;
		this.metadata = metadata;
	}

	/**
	 * The name of the question this result answers.
	 */
	public String getName() {
		return this.name;
	}

	@Override
	public DecisionAnswer getOutput() {
		return this.answer;
	}

	@Override
	public DecisionResultMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DecisionResult that)) {
			return false;
		}
		return this.name.equals(that.name) && this.answer.equals(that.answer) && this.metadata.equals(that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.answer, this.metadata);
	}

	@Override
	public String toString() {
		return "DecisionResult{name='" + this.name + "', answer=" + this.answer + "}";
	}

}
