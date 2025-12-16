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

package org.springframework.ai.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A composite {@link ResponseTextCleaner} that applies multiple cleaners in sequence.
 * This allows for a flexible pipeline of text cleaning operations.
 *
 * @author liugddx
 * @since 1.1.0
 */
public class CompositeResponseTextCleaner implements ResponseTextCleaner {

	private final List<ResponseTextCleaner> cleaners;

	/**
	 * Creates a composite cleaner with the given cleaners.
	 * @param cleaners the list of cleaners to apply in order
	 */
	public CompositeResponseTextCleaner(List<ResponseTextCleaner> cleaners) {
		Assert.notNull(cleaners, "cleaners cannot be null");
		this.cleaners = new ArrayList<>(cleaners);
	}

	/**
	 * Creates a composite cleaner with no cleaners. Text will be returned unchanged.
	 */
	public CompositeResponseTextCleaner() {
		this(new ArrayList<>());
	}

	/**
	 * Creates a composite cleaner with the given cleaners.
	 * @param cleaners the cleaners to apply in order
	 */
	public CompositeResponseTextCleaner(ResponseTextCleaner... cleaners) {
		this(Arrays.asList(cleaners));
	}

	@Override
	public @Nullable String clean(@Nullable String text) {
		String result = text;
		for (ResponseTextCleaner cleaner : this.cleaners) {
			result = cleaner.clean(result);
		}
		return result;
	}

	/**
	 * Creates a builder for constructing a composite cleaner.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link CompositeResponseTextCleaner}.
	 */
	public static final class Builder {

		private final List<ResponseTextCleaner> cleaners = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Add a cleaner to the pipeline.
		 * @param cleaner the cleaner to add
		 * @return this builder
		 */
		public Builder addCleaner(ResponseTextCleaner cleaner) {
			Assert.notNull(cleaner, "cleaner cannot be null");
			this.cleaners.add(cleaner);
			return this;
		}

		/**
		 * Build the composite cleaner.
		 * @return a new composite cleaner instance
		 */
		public CompositeResponseTextCleaner build() {
			return new CompositeResponseTextCleaner(this.cleaners);
		}

	}

}
