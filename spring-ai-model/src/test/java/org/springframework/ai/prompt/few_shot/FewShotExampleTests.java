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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link FewShotExample}.
 */
public class FewShotExampleTests {

	private static final String ID = "ex1";

	private static final String INPUT = "What is Spring?";

	private static final String OUTPUT = "Spring is a framework for building Java applications.";

	private static final double RELEVANCE_SCORE = 0.95;

	@Test
	public void testBuilderCreatesValidExample() {
		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.relevanceScore(RELEVANCE_SCORE)
			.build();

		assertThat(example.getId()).isEqualTo(ID);
		assertThat(example.getInput()).isEqualTo(INPUT);
		assertThat(example.getOutput()).isEqualTo(OUTPUT);
		assertThat(example.getRelevanceScore()).isEqualTo(RELEVANCE_SCORE);
	}

	@Test
	public void testBuilderWithMetadata() {
		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata("domain", "technical")
			.metadata("difficulty", "beginner")
			.build();

		Map<String, Object> metadata = example.getMetadata();
		assertThat(metadata).hasSize(2);
		assertThat(metadata).containsEntry("domain", "technical");
		assertThat(metadata).containsEntry("difficulty", "beginner");
	}

	@Test
	public void testBuilderWithMetadataMap() {
		Map<String, Object> metadataMap = new HashMap<>();
		metadataMap.put("domain", "technical");
		metadataMap.put("difficulty", "intermediate");

		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata(metadataMap)
			.build();

		assertThat(example.getMetadata()).isEqualTo(metadataMap);
	}

	@Test
	public void testImmutabilityOfMetadata() {
		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata("domain", "technical")
			.build();

		Map<String, Object> retrievedMetadata = example.getMetadata();
		retrievedMetadata.put("domain", "modified");

		// Verify original metadata is unchanged
		assertThat(example.getMetadata()).containsEntry("domain", "technical");
	}

	@Test
	public void testDefensiveCopyOnConstruction() {
		Map<String, Object> metadataMap = new HashMap<>();
		metadataMap.put("key", "value");

		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata(metadataMap)
			.build();

		// Modify original map
		metadataMap.put("key", "modified");
		metadataMap.put("newKey", "newValue");

		// Verify example metadata is unchanged
		Map<String, Object> exampleMetadata = example.getMetadata();
		assertThat(exampleMetadata).containsEntry("key", "value");
		assertThat(exampleMetadata).doesNotContainKey("newKey");
	}

	@Test
	public void testDefaultRelevanceScoreIsZero() {
		FewShotExample example = FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).build();

		assertThat(example.getRelevanceScore()).isEqualTo(0.0);
	}

	@Test
	public void testDefaultMetadataIsEmpty() {
		FewShotExample example = FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).build();

		assertThat(example.getMetadata()).isEmpty();
	}

	@Test
	public void testBuilderValidatesId() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id(null).input(INPUT).output(OUTPUT).build());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id("").input(INPUT).output(OUTPUT).build());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id("   ").input(INPUT).output(OUTPUT).build());
	}

	@Test
	public void testBuilderValidatesInput() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id(ID).input(null).output(OUTPUT).build());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id(ID).input("").output(OUTPUT).build());
	}

	@Test
	public void testBuilderValidatesOutput() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id(ID).input(INPUT).output(null).build());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> FewShotExample.builder().id(ID).input(INPUT).output("").build());
	}

	@Test
	public void testBuildValidatesAllRequiredFields() {
		assertThatIllegalArgumentException().isThrownBy(() -> FewShotExample.builder().build());

		assertThatIllegalArgumentException().isThrownBy(() -> FewShotExample.builder().id(ID).build());

		assertThatIllegalArgumentException().isThrownBy(() -> FewShotExample.builder().id(ID).input(INPUT).build());
	}

	@Test
	public void testMetadataValidatesKeyAndValue() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).metadata(null, "value").build());

		assertThatIllegalArgumentException().isThrownBy(
				() -> FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).metadata("key", null).build());
	}

	@Test
	public void testEqualsAndHashCode() {
		FewShotExample example1 = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata("domain", "technical")
			.relevanceScore(0.95)
			.build();

		FewShotExample example2 = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata("domain", "technical")
			.relevanceScore(0.95)
			.build();

		assertThat(example1).isEqualTo(example2);
		assertThat(example1.hashCode()).isEqualTo(example2.hashCode());
	}

	@Test
	public void testEqualsWithDifferentId() {
		FewShotExample example1 = FewShotExample.builder().id("ex1").input(INPUT).output(OUTPUT).build();

		FewShotExample example2 = FewShotExample.builder().id("ex2").input(INPUT).output(OUTPUT).build();

		assertThat(example1).isNotEqualTo(example2);
	}

	@Test
	public void testEqualsWithDifferentRelevanceScore() {
		FewShotExample example1 = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.relevanceScore(0.95)
			.build();

		FewShotExample example2 = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.relevanceScore(0.85)
			.build();

		assertThat(example1).isNotEqualTo(example2);
	}

	@Test
	public void testEqualsWithNull() {
		FewShotExample example = FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).build();

		assertThat(example).isNotEqualTo(null);
	}

	@Test
	public void testEqualsWithDifferentType() {
		FewShotExample example = FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).build();

		assertThat(example).isNotEqualTo("not an example");
	}

	@Test
	public void testToString() {
		FewShotExample example = FewShotExample.builder().id(ID).input(INPUT).output(OUTPUT).build();

		String str = example.toString();
		assertThat(str).contains("FewShotExample");
		assertThat(str).contains(ID);
		assertThat(str).contains(INPUT);
		assertThat(str).contains(OUTPUT);
	}

	@Test
	public void testBuilderChaining() {
		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.metadata("key1", "value1")
			.metadata("key2", "value2")
			.metadata("key3", "value3")
			.relevanceScore(0.85)
			.build();

		assertThat(example.getMetadata()).hasSize(3);
		assertThat(example.getRelevanceScore()).isEqualTo(0.85);
	}

	@Test
	public void testNegativeRelevanceScore() {
		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.relevanceScore(-0.5)
			.build();

		assertThat(example.getRelevanceScore()).isEqualTo(-0.5);
	}

	@Test
	public void testHighRelevanceScore() {
		FewShotExample example = FewShotExample.builder()
			.id(ID)
			.input(INPUT)
			.output(OUTPUT)
			.relevanceScore(1.5)
			.build();

		assertThat(example.getRelevanceScore()).isEqualTo(1.5);
	}

	@Test
	public void testLongInputAndOutput() {
		String longInput = "A".repeat(1000);
		String longOutput = "B".repeat(2000);

		FewShotExample example = FewShotExample.builder().id(ID).input(longInput).output(longOutput).build();

		assertThat(example.getInput()).isEqualTo(longInput);
		assertThat(example.getOutput()).isEqualTo(longOutput);
	}

}
