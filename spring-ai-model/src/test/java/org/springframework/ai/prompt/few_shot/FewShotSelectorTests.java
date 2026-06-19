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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for FewShotSelector implementations.
 */
public class FewShotSelectorTests {

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
					.input("Explain MVC")
					.output("MVC separates concerns.")
					.metadata("domain", "architecture")
					.build(),
				FewShotExample.builder()
					.id("ex3")
					.input("What is dependency injection?")
					.output("DI is a design pattern.")
					.metadata("domain", "technical")
					.build(),
				FewShotExample.builder()
					.id("ex4")
					.input("What is REST?")
					.output("REST is an architectural style.")
					.metadata("domain", "architecture")
					.build());
	}

	// ============ RandomFewShotSelector Tests ============

	@Test
	public void testRandomSelectorSelectsRequestedCount() {
		FewShotSelector selector = new RandomFewShotSelector();
		List<FewShotExample> selected = selector.select("query", this.examples, 2);

		assertThat(selected).hasSize(2);
		assertThat(selected).allMatch(e -> this.examples.contains(e));
	}

	@Test
	public void testRandomSelectorReturnsEmptyWhenMaxIsZero() {
		FewShotSelector selector = new RandomFewShotSelector();
		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("query", this.examples, 0));
	}

	@Test
	public void testRandomSelectorHandlesEmptyExamples() {
		FewShotSelector selector = new RandomFewShotSelector();
		List<FewShotExample> selected = selector.select("query", new ArrayList<>(), 2);

		assertThat(selected).isEmpty();
	}

	@Test
	public void testRandomSelectorLimitsToAvailableCount() {
		FewShotSelector selector = new RandomFewShotSelector();
		List<FewShotExample> selected = selector.select("query", this.examples, 10);

		assertThat(selected).hasSize(4); // Only 4 examples available
	}

	@Test
	public void testRandomSelectorWithSeededRandom() {
		// Same seed = same results
		Random random1 = new Random(42);
		Random random2 = new Random(42);

		FewShotSelector selector1 = new RandomFewShotSelector(random1);
		FewShotSelector selector2 = new RandomFewShotSelector(random2);

		List<FewShotExample> selected1 = selector1.select("query", this.examples, 2);
		List<FewShotExample> selected2 = selector2.select("query", this.examples, 2);

		assertThat(selected1).hasSameSizeAs(selected2);
		// Should have same IDs in same order
		assertThat(selected1.stream().map(FewShotExample::getId))
			.containsExactlyElementsOf(selected2.stream().map(FewShotExample::getId).toList());
	}

	@Test
	public void testRandomSelectorValidatesUserQuery() {
		FewShotSelector selector = new RandomFewShotSelector();

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select(null, this.examples, 2));

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("", this.examples, 2));

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("   ", this.examples, 2));
	}

	@Test
	public void testRandomSelectorValidatesAvailableExamples() {
		FewShotSelector selector = new RandomFewShotSelector();

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("query", null, 2));
	}

	// ============ MetadataBasedFewShotSelector Tests ============

	@Test
	public void testMetadataSelectorFiltersCorrectly() {
		FewShotSelector selector = new MetadataBasedFewShotSelector("domain", "technical");
		List<FewShotExample> selected = selector.select("query", this.examples, 5);

		assertThat(selected).hasSize(2); // ex1 and ex3
		assertThat(selected).allMatch(e -> "technical".equals(e.getMetadata().get("domain")));
	}

	@Test
	public void testMetadataSelectorRespectsMaxExamples() {
		FewShotSelector selector = new MetadataBasedFewShotSelector("domain", "technical");
		List<FewShotExample> selected = selector.select("query", this.examples, 1);

		assertThat(selected).hasSize(1);
	}

	@Test
	public void testMetadataSelectorReturnsEmptyWhenNoMatch() {
		FewShotSelector selector = new MetadataBasedFewShotSelector("domain", "nonexistent");
		List<FewShotExample> selected = selector.select("query", this.examples, 5);

		assertThat(selected).isEmpty();
	}

	@Test
	public void testMetadataSelectorValidatesUserQuery() {
		FewShotSelector selector = new MetadataBasedFewShotSelector("domain", "technical");

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select(null, this.examples, 2));
	}

	@Test
	public void testMetadataSelectorValidatesAvailableExamples() {
		FewShotSelector selector = new MetadataBasedFewShotSelector("domain", "technical");

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("query", null, 2));
	}

	@Test
	public void testMetadataSelectorWithIntegerValue() {
		List<FewShotExample> examplesWithIntMetadata = Arrays.asList(
				FewShotExample.builder().id("ex1").input("q1").output("a1").metadata("level", 1).build(),
				FewShotExample.builder().id("ex2").input("q2").output("a2").metadata("level", 2).build());

		FewShotSelector selector = new MetadataBasedFewShotSelector("level", 1);
		List<FewShotExample> selected = selector.select("query", examplesWithIntMetadata, 5);

		assertThat(selected).hasSize(1);
		assertThat(selected.get(0).getId()).isEqualTo("ex1");
	}

	@Test
	public void testMetadataSelectorConstructorValidation() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MetadataBasedFewShotSelector(null, "value"));

		assertThatIllegalArgumentException().isThrownBy(() -> new MetadataBasedFewShotSelector("key", null));
	}

	@Test
	public void testMetadataSelectorGetters() {
		MetadataBasedFewShotSelector selector = new MetadataBasedFewShotSelector("domain", "technical");

		assertThat(selector.getMetadataKey()).isEqualTo("domain");
		assertThat(selector.getMetadataValue()).isEqualTo("technical");
	}

	// ============ General Interface Tests ============

	@Test
	public void testSelectorValidatesMaxExamplesGreaterThanZero() {
		FewShotSelector randomSelector = new RandomFewShotSelector();
		FewShotSelector metadataSelector = new MetadataBasedFewShotSelector("key", "value");

		assertThatIllegalArgumentException().isThrownBy(() -> randomSelector.select("query", this.examples, -1));

		assertThatIllegalArgumentException().isThrownBy(() -> metadataSelector.select("query", this.examples, 0));
	}

	@Test
	public void testSelectorsReturnNewListInstances() {
		FewShotSelector selector = new RandomFewShotSelector();

		List<FewShotExample> selected1 = selector.select("query", this.examples, 2);
		List<FewShotExample> selected2 = selector.select("query", this.examples, 2);

		assertThat(selected1).isNotSameAs(selected2);
	}

}
