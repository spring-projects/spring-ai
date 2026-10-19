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

/**
 * Default implementation of {@link DecisionOptions}.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class DefaultDecisionOptions implements DecisionOptions {

	private final @Nullable String model;

	protected DefaultDecisionOptions(@Nullable String model) {
		this.model = model;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public DecisionOptions.Builder<?> mutate() {
		return DecisionOptions.builder().model(this.model);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DefaultDecisionOptions that = (DefaultDecisionOptions) o;
		return Objects.equals(this.model, that.model);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.model);
	}

	@Override
	public String toString() {
		return "DefaultDecisionOptions{model='" + this.model + "'}";
	}

	/**
	 * Default builder. Provider builders extend it with their own fields and override
	 * {@link #combineWith(DecisionOptions.Builder)} to merge them.
	 */
	public static class Builder<B extends Builder<B>> implements DecisionOptions.Builder<B> {

		protected @Nullable String model;

		public Builder() {
		}

		@Override
		@SuppressWarnings("unchecked")
		public B clone() {
			try {
				return (B) super.clone();
			}
			catch (CloneNotSupportedException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@SuppressWarnings("unchecked")
		protected B self() {
			return (B) this;
		}

		@Override
		public B model(@Nullable String model) {
			this.model = model;
			return self();
		}

		@Override
		public B combineWith(DecisionOptions.Builder<?> other) {
			if (other instanceof Builder<?> that && that.model != null) {
				this.model = that.model;
			}
			return self();
		}

		@Override
		public DecisionOptions build() {
			return new DefaultDecisionOptions(this.model);
		}

	}

}
