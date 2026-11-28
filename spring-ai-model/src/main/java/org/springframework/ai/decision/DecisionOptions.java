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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelOptions;

/**
 * Portable options for decision models. Options are immutable: {@link #mutate()} returns
 * a builder with their values, and {@link Builder#combineWith(Builder)} overrides them
 * with the non-null values of another builder.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public interface DecisionOptions extends ModelOptions {

	/**
	 * The name of the model to use.
	 */
	@Nullable String getModel();

	/**
	 * A builder initialized with these options.
	 */
	DecisionOptions.Builder<?> mutate();

	static DecisionOptions.Builder<?> builder() {
		return new DefaultDecisionOptions.Builder<>();

	}

	interface Builder<B extends Builder<B>> extends Cloneable {

		B clone();

		B model(@Nullable String model);

		DecisionOptions build();

		/**
		 * Override the values set so far with the non-null values of the given builder,
		 * whichever implementation it is.
		 */
		B combineWith(DecisionOptions.Builder<?> other);

	}

}
