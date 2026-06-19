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

package org.springframework.ai.prompt.few_shot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Represents a single few-shot example used in prompt engineering.
 *
 * A FewShotExample is an immutable domain object containing a user query example, a model
 * response example, and optional metadata for organizing and selecting examples.
 *
 * <p>
 * Few-shot learning enables models to quickly understand tasks by providing a few input
 * and output examples. This class encapsulates those examples with support for semantic
 * selection and filtering.
 *
 * <p>
 * Example of creating a few-shot example: <pre>{@code
 * // Using builder
 * FewShotExample example = FewShotExample.builder()
 *     .id("ex1")
 *     .input("What is Spring?")
 *     .output("Spring is a framework for building Java applications...")
 *     .metadata("domain", "technical")
 *     .metadata("difficulty", "beginner")
 *     .relevanceScore(0.95)
 *     .build();
 * }</pre>
 *
 * <p>
 * FewShotExample is immutable and thread-safe. All fields are final and metadata is
 * defensively copied.
 *
 * @author galt-k
 * @since 1.0
 */
public final class FewShotExample {

	/**
	 * Unique identifier for this example.
	 */
	private final String id;

	/**
	 * The user query example.
	 */
	private final String input;

	/**
	 * The model response example.
	 */
	private final String output;

	/**
	 * Metadata associated with this example.
	 * <p>
	 * Common metadata includes:
	 * <ul>
	 * <li>domain: The domain or category of the example (e.g., "technical", "financial")
	 * <li>difficulty: Difficulty level (e.g., "beginner", "intermediate", "advanced")
	 * <li>language: Programming language for code examples
	 * <li>tags: Comma-separated tags for organizing examples
	 * </ul>
	 */
	private final Map<String, Object> metadata;

	/**
	 * A numeric relevance score associated with this example.
	 * <p>
	 * Common uses include:
	 * <ul>
	 * <li>Semantic similarity score when comparing with user queries
	 * <li>Custom relevance ranking from selection strategies
	 * <li>Confidence scores from ML-based selection
	 * </ul>
	 * <p>
	 * Higher values typically indicate greater relevance.
	 */
	private final double relevanceScore;

	private FewShotExample(String id, String input, String output, Map<String, Object> metadata,
			double relevanceScore) {
		Assert.hasText(id, "id cannot be null or empty");
		Assert.hasText(input, "input cannot be null or empty");
		Assert.hasText(output, "output cannot be null or empty");
		Assert.notNull(metadata, "metadata cannot be null");

		this.id = id;
		this.input = input;
		this.output = output;
		this.metadata = new HashMap<>(metadata);
		this.relevanceScore = relevanceScore;
	}

	/**
	 * Creates a new builder for constructing a FewShotExample.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the unique identifier for this example.
	 * @return the unique identifier
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Returns the user query example.
	 * @return the input example
	 */
	public String getInput() {
		return this.input;
	}

	/**
	 * Returns the model response example.
	 * @return the output example
	 */
	public String getOutput() {
		return this.output;
	}

	/**
	 * Returns a copy of the metadata associated with this example.
	 * @return a defensive copy of the metadata map
	 */
	public Map<String, Object> getMetadata() {
		return new HashMap<>(this.metadata);
	}

	/**
	 * Returns the relevance score associated with this example.
	 * @return the relevance score
	 */
	public double getRelevanceScore() {
		return this.relevanceScore;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}
		FewShotExample example = (FewShotExample) o;
		return Objects.equals(this.id, example.id) && Objects.equals(this.input, example.input)
				&& Objects.equals(this.output, example.output) && Objects.equals(this.metadata, example.metadata)
				&& Double.compare(example.relevanceScore, this.relevanceScore) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.input, this.output, this.metadata, this.relevanceScore);
	}

	@Override
	public String toString() {
		return "FewShotExample{" + "id='" + this.id + '\'' + ", input='" + this.input + '\'' + ", output='"
				+ this.output + '\'' + ", metadata=" + this.metadata + ", relevanceScore=" + this.relevanceScore + '}';
	}

	/**
	 * Builder for constructing a FewShotExample with a fluent API.
	 *
	 * <p>
	 * The builder provides a convenient way to construct a FewShotExample with optional
	 * parameters. At minimum, an id, input, and output must be provided.
	 *
	 * <p>
	 * Example usage: <pre>{@code
	 * FewShotExample example = FewShotExample.builder()
	 *     .id("ex1")
	 *     .input("User question")
	 *     .output("Model response")
	 *     .metadata("domain", "technical")
	 *     .relevanceScore(0.95)
	 *     .build();
	 * }</pre>
	 */
	public static final class Builder {

		private @Nullable String id;

		private @Nullable String input;

		private @Nullable String output;

		private Map<String, Object> metadata = new HashMap<>();

		private double relevanceScore = 0.0;

		/**
		 * Sets the unique identifier for the example.
		 * @param id the unique identifier, must not be empty
		 * @return this builder instance
		 * @throws IllegalArgumentException if id is null or empty
		 */
		public Builder id(String id) {
			Assert.hasText(id, "id cannot be null or empty");
			this.id = id;
			return this;
		}

		/**
		 * Sets the user query example.
		 * @param input the input example, must not be empty
		 * @return this builder instance
		 * @throws IllegalArgumentException if input is null or empty
		 */
		public Builder input(String input) {
			Assert.hasText(input, "input cannot be null or empty");
			this.input = input;
			return this;
		}

		/**
		 * Sets the model response example.
		 * @param output the output example, must not be empty
		 * @return this builder instance
		 * @throws IllegalArgumentException if output is null or empty
		 */
		public Builder output(String output) {
			Assert.hasText(output, "output cannot be null or empty");
			this.output = output;
			return this;
		}

		/**
		 * Adds a metadata key-value pair to the example.
		 * @param key the metadata key, must not be null
		 * @param value the metadata value, must not be null
		 * @return this builder instance
		 * @throws IllegalArgumentException if key or value is null
		 */
		public Builder metadata(String key, Object value) {
			Assert.notNull(key, "metadata key cannot be null");
			Assert.notNull(value, "metadata value cannot be null");
			this.metadata.put(key, value);
			return this;
		}

		/**
		 * Sets all metadata at once, replacing any previously set metadata.
		 * @param metadata the metadata map, must not be null
		 * @return this builder instance
		 * @throws IllegalArgumentException if metadata is null
		 */
		public Builder metadata(Map<String, Object> metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			this.metadata = new HashMap<>(metadata);
			return this;
		}

		/**
		 * Sets the relevance score for the example.
		 * @param score the relevance score, typically between 0.0 and 1.0
		 * @return this builder instance
		 */
		public Builder relevanceScore(double score) {
			this.relevanceScore = score;
			return this;
		}

		/**
		 * Builds and returns a new FewShotExample with the configured values.
		 * @return a new FewShotExample instance
		 * @throws IllegalArgumentException if required fields (id, input, output) are not
		 * set
		 */
		public FewShotExample build() {
			Assert.hasText(this.id, "id is required");
			Assert.hasText(this.input, "input is required");
			Assert.hasText(this.output, "output is required");
			return new FewShotExample(this.id, this.input, this.output, this.metadata, this.relevanceScore);
		}

	}

}
