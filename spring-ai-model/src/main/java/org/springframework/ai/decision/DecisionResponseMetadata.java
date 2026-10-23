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

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.model.MutableResponseMetadata;
import org.springframework.util.Assert;

/**
 * Metadata associated with a {@link DecisionResponse}: the response id and the model that
 * answered, when the provider reports them, and the token usage.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class DecisionResponseMetadata extends MutableResponseMetadata {

	private final @Nullable String id;

	private final @Nullable String model;

	private final Usage usage;

	/**
	 * For subclasses, such as provider-specific metadata; use {@link #builder()}
	 * otherwise.
	 */
	protected DecisionResponseMetadata(@Nullable String id, @Nullable String model, Usage usage) {
		Assert.notNull(usage, "usage cannot be null");
		this.id = id;
		this.model = model;
		this.usage = usage;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The response id, or {@code null} when the provider doesn't report one.
	 */
	public @Nullable String getId() {
		return this.id;
	}

	/**
	 * The model that answered, or {@code null} when the provider doesn't report it.
	 */
	public @Nullable String getModel() {
		return this.model;
	}

	public Usage getUsage() {
		return this.usage;
	}

	@Override
	public String toString() {
		return "DecisionResponseMetadata{id='" + this.id + "', model='" + this.model + "', usage=" + this.usage + "}";
	}

	public static final class Builder {

		private @Nullable String id;

		private @Nullable String model;

		private Usage usage = new EmptyUsage();

		private Builder() {
		}

		public Builder id(@Nullable String id) {
			this.id = id;
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder usage(Usage usage) {
			this.usage = usage;
			return this;
		}

		public DecisionResponseMetadata build() {
			return new DecisionResponseMetadata(this.id, this.model, this.usage);
		}

	}

}
