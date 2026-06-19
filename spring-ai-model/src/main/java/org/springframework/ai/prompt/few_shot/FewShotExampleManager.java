/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * Orchestrator for managing few-shot examples with selection and template injection.
 *
 * FewShotExampleManager is the main facade for the few-shot learning feature, providing a
 * unified interface for selecting and formatting examples for prompt injection.
 *
 * <p>
 * This class combines:
 * <ul>
 * <li>Example pool management
 * <li>Dynamic selection via pluggable strategies
 * <li>Template-based formatting for prompt injection
 * <li>Builder pattern for flexible configuration
 * </ul>
 *
 * <p>
 * Example usage: <pre>{@code
 * List<FewShotExample> examples = Arrays.asList(
 *     FewShotExample.builder()
 *         .id("ex1")
 *         .input("What is Spring?")
 *         .output("Spring is a framework.")
 *         .build(),
 *     FewShotExample.builder()
 *         .id("ex2")
 *         .input("What is REST?")
 *         .output("REST is an architectural style.")
 *         .build()
 * );
 *
 * FewShotExampleManager manager = FewShotExampleManager.builder(examples)
 *     .selector(new SemanticSimilarityFewShotSelector(embeddingModel))
 *     .maxExamples(3)
 *     .build();
 *
 * String formattedExamples = manager.getFormattedExamples("Explain Spring Framework");
 * }</pre>
 *
 * @author galt-k
 * @since 1.0
 */
public final class FewShotExampleManager {

	/**
	 * Default template for injecting examples into prompts.
	 */
	public static final String DEFAULT_EXAMPLES_TEMPLATE = """
			Here are some examples to help you understand the task:

			{examples}

			Now, respond to the user's request following the same format as the examples above.""";

	private final List<FewShotExample> examplePool;

	private final FewShotSelector selector;

	private final String examplesTemplate;

	private final int maxExamples;

	private FewShotExampleManager(List<FewShotExample> examplePool, FewShotSelector selector, String examplesTemplate,
			int maxExamples) {
		Assert.notEmpty(examplePool, "examplePool cannot be empty");
		Assert.notNull(selector, "selector cannot be null");
		Assert.hasText(examplesTemplate, "examplesTemplate cannot be empty");
		Assert.isTrue(maxExamples > 0, "maxExamples must be greater than 0");

		this.examplePool = new ArrayList<>(examplePool);
		this.selector = selector;
		this.examplesTemplate = examplesTemplate;
		this.maxExamples = maxExamples;
	}

	/**
	 * Creates a new builder for FewShotExampleManager.
	 * @param examplePool the pool of examples to choose from, must not be empty
	 * @return a new builder instance
	 */
	public static Builder builder(List<FewShotExample> examplePool) {
		return new Builder(examplePool);
	}

	/**
	 * Selects and formats examples relevant to the user query.
	 *
	 * This method:
	 * <ol>
	 * <li>Uses the configured selector to choose relevant examples
	 * <li>Formats each example as "Input: ... \nOutput: ..."
	 * <li>Joins examples with separator
	 * </ol>
	 * @param userQuery the user's query for selecting relevant examples
	 * @return formatted examples as a string ready for template injection
	 */
	public String getFormattedExamples(String userQuery) {
		Assert.hasText(userQuery, "userQuery cannot be empty");

		// Step 1: Select relevant examples using strategy
		List<FewShotExample> selected = this.selector.select(userQuery, this.examplePool, this.maxExamples);

		// Step 2: Format selected examples
		return selected.stream()
			.map(example -> String.format("Input: %s\n\nOutput: %s", example.getInput(), example.getOutput()))
			.collect(Collectors.joining("\n\n---\n\n"));
	}

	/**
	 * Gets the example pool size.
	 * @return the number of examples in the pool
	 */
	public int getExamplePoolSize() {
		return this.examplePool.size();
	}

	/**
	 * Gets the current selector being used.
	 * @return the FewShotSelector instance
	 */
	public FewShotSelector getSelector() {
		return this.selector;
	}

	/**
	 * Gets the examples template.
	 * @return the template string with {examples} placeholder
	 */
	public String getExamplesTemplate() {
		return this.examplesTemplate;
	}

	/**
	 * Gets the maximum number of examples to select.
	 * @return the max examples count
	 */
	public int getMaxExamples() {
		return this.maxExamples;
	}

	/**
	 * Builder for FewShotExampleManager with fluent API.
	 */
	public static final class Builder {

		private final List<FewShotExample> examplePool;

		private FewShotSelector selector;

		private String examplesTemplate;

		private int maxExamples;

		/**
		 * Creates a new builder with the example pool.
		 * @param examplePool the pool of examples, must not be empty
		 */
		public Builder(List<FewShotExample> examplePool) {
			Assert.notEmpty(examplePool, "examplePool cannot be empty");
			this.examplePool = examplePool;
			// Set defaults
			this.selector = new RandomFewShotSelector();
			this.examplesTemplate = DEFAULT_EXAMPLES_TEMPLATE;
			this.maxExamples = 3;
		}

		/**
		 * Sets the selector strategy for example selection.
		 * @param selector the FewShotSelector to use, must not be null
		 * @return this builder instance
		 */
		public Builder selector(FewShotSelector selector) {
			Assert.notNull(selector, "selector cannot be null");
			this.selector = selector;
			return this;
		}

		/**
		 * Sets the template for injecting examples.
		 * @param template the template string with {examples} placeholder, must not be
		 * empty
		 * @return this builder instance
		 */
		public Builder examplesTemplate(String template) {
			Assert.hasText(template, "template cannot be empty");
			this.examplesTemplate = template;
			return this;
		}

		/**
		 * Sets the maximum number of examples to select.
		 * @param max the maximum count, must be greater than 0
		 * @return this builder instance
		 */
		public Builder maxExamples(int max) {
			Assert.isTrue(max > 0, "maxExamples must be greater than 0");
			this.maxExamples = max;
			return this;
		}

		/**
		 * Builds the FewShotExampleManager with configured settings.
		 * @return a new FewShotExampleManager instance
		 */
		public FewShotExampleManager build() {
			return new FewShotExampleManager(this.examplePool, this.selector, this.examplesTemplate, this.maxExamples);
		}

	}

}
