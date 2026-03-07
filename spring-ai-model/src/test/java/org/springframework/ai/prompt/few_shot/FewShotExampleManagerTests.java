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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for FewShotExampleManager (Facade pattern).
 */
public class FewShotExampleManagerTests {

	private List<FewShotExample> examples;

	@BeforeEach
	public void setUp() {
		this.examples = Arrays.asList(
				FewShotExample.builder()
					.id("ex1")
					.input("What is Spring?")
					.output("Spring is a framework.")
					.metadata("domain", "technical")
					.build(),
				FewShotExample.builder()
					.id("ex2")
					.input("What is REST?")
					.output("REST is an architectural style.")
					.metadata("domain", "technical")
					.build(),
				FewShotExample.builder()
					.id("ex3")
					.input("What is MVC?")
					.output("MVC separates concerns.")
					.metadata("domain", "architecture")
					.build());
	}

	@Test
	public void testBuilderCreatesManagerWithDefaults() {
		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples).build();

		assertThat(manager.getExamplePoolSize()).isEqualTo(3);
		assertThat(manager.getMaxExamples()).isEqualTo(3);
		assertThat(manager.getSelector()).isNotNull();
	}

	@Test
	public void testGetFormattedExamplesReturnsString() {
		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples).build();

		String formatted = manager.getFormattedExamples("What is Spring Framework?");

		assertThat(formatted).isNotEmpty();
		assertThat(formatted).contains("Input:");
		assertThat(formatted).contains("Output:");
	}

	@Test
	public void testCustomSelector() {
		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples)
			.selector(new MetadataBasedFewShotSelector("domain", "technical"))
			.maxExamples(2)
			.build();

		String formatted = manager.getFormattedExamples("query");

		// Should only have technical examples
		assertThat(formatted).contains("Spring");
		assertThat(formatted).contains("REST");
	}

	@Test
	public void testCustomTemplate() {
		String customTemplate = "Examples:\n{examples}";

		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples)
			.examplesTemplate(customTemplate)
			.build();

		assertThat(manager.getExamplesTemplate()).isEqualTo(customTemplate);
	}

	@Test
	public void testMaxExamplesLimit() {
		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples).maxExamples(1).build();

		String formatted = manager.getFormattedExamples("query");

		// Should contain exactly one example (one "Input:" and one "Output:")
		int inputCount = (int) formatted.split("Input:").length - 1;
		assertThat(inputCount).isEqualTo(1);
	}

	@Test
	public void testBuilderValidatesExamplePool() {
		assertThatIllegalArgumentException().isThrownBy(() -> FewShotExampleManager.builder(Arrays.asList()).build());
	}

	@Test
	public void testBuilderValidatesSelector() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExampleManager.builder(this.examples).selector(null).build());
	}

	@Test
	public void testBuilderValidatesTemplate() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExampleManager.builder(this.examples).examplesTemplate("").build());
	}

	@Test
	public void testBuilderValidatesMaxExamples() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExampleManager.builder(this.examples).maxExamples(0).build());
	}

	@Test
	public void testGetFormattedExamplesValidatesQuery() {
		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples).build();

		assertThatIllegalArgumentException().isThrownBy(() -> manager.getFormattedExamples(""));
	}

	@Test
	public void testGettersWorkCorrectly() {
		FewShotSelector customSelector = new RandomFewShotSelector();
		String customTemplate = "Custom: {examples}";

		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples)
			.selector(customSelector)
			.examplesTemplate(customTemplate)
			.maxExamples(5)
			.build();

		assertThat(manager.getSelector()).isSameAs(customSelector);
		assertThat(manager.getExamplesTemplate()).isEqualTo(customTemplate);
		assertThat(manager.getMaxExamples()).isEqualTo(5);
	}

	@Test
	public void testFormattingIncludesInputAndOutput() {
		FewShotExampleManager manager = FewShotExampleManager.builder(this.examples)
			.selector(new RandomFewShotSelector())
			.maxExamples(1)
			.build();

		String formatted = manager.getFormattedExamples("query");

		assertThat(formatted).contains("Input:");
		assertThat(formatted).contains("Output:");
	}

}
