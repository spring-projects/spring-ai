/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.moderation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * The Moderation class represents the result of a moderation process. It contains the
 * moderation ID, model, and a list of moderation results. To create an instance of
 * Moderation, use the Builder class.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public final class Moderation {

	private final String id;

	private final String model;

	private final List<ModerationResult> results;

	private Moderation(Builder builder) {
		Assert.state(builder.id != null, "id is required");
		Assert.state(builder.model != null, "model is required");
		this.id = builder.id;
		this.model = builder.model;
		this.results = builder.moderationResultList;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getId() {
		return this.id;
	}

	public String getModel() {
		return this.model;
	}

	public List<ModerationResult> getResults() {
		return this.results;
	}

	@Override
	public String toString() {
		return "Moderation{" + "id='" + this.id + '\'' + ", model='" + this.model + '\'' + ", results="
				+ Arrays.toString(this.results.toArray()) + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Moderation that)) {
			return false;
		}
		return Objects.equals(this.id, that.id) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.results, that.results);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.model, this.results);
	}

	public static final class Builder {

		private @Nullable String id;

		private @Nullable String model;

		private List<ModerationResult> moderationResultList = new ArrayList<>();

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder results(List<ModerationResult> results) {
			this.moderationResultList = results;
			return this;
		}

		public Moderation build() {
			return new Moderation(this);
		}

	}

}
