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

package org.springframework.ai.decision.answer;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The answer to a question the provider explicitly declined to answer. A refusal is not a
 * request failure: the other questions in the same request are answered as usual.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class RefusalAnswer implements DecisionAnswer {

	private final @Nullable String reason;

	private RefusalAnswer(@Nullable String reason) {
		this.reason = reason;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The reason the provider gave, or {@code null} when it gave none.
	 */
	public @Nullable String getReason() {
		return this.reason;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof RefusalAnswer that)) {
			return false;
		}
		return Objects.equals(this.reason, that.reason);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.reason);
	}

	@Override
	public String toString() {
		return "RefusalAnswer{reason='" + this.reason + "'}";
	}

	public static final class Builder {

		private @Nullable String reason;

		private Builder() {
		}

		public Builder reason(@Nullable String reason) {
			this.reason = reason;
			return this;
		}

		public RefusalAnswer build() {
			return new RefusalAnswer(this.reason);
		}

	}

}
