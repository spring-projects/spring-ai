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

package org.springframework.ai.document.id;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IdGeneratorProviderTest {

	@Test
	void hashGeneratorGenerateSimilarIdsForSimilarContent() {

		var idGenerator1 = new JdkSha256HexIdGenerator();
		var idGenerator2 = new JdkSha256HexIdGenerator();

		final String content = "Content";
		final Map<String, Object> metadata = Map.of("metadata", Set.of("META_DATA"));

		String actualHashes1 = idGenerator1.generateId(content, metadata);
		String actualHashes2 = idGenerator2.generateId(content, metadata);

		Assertions.assertEquals(actualHashes1, actualHashes2);

		// Assert (other expected behaviors)
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes1));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes2));
	}

	@Test
	void hashGeneratorGenerateDifferentIdsForDifferentContent() {

		var idGenerator1 = new JdkSha256HexIdGenerator();
		var idGenerator2 = new JdkSha256HexIdGenerator();

		final String content1 = "Content";
		final Map<String, Object> metadata1 = Map.of("metadata", Set.of("META_DATA"));
		final String content2 = content1 + " ";
		final Map<String, Object> metadata2 = metadata1;

		String actualHashes1 = idGenerator1.generateId(content1, metadata1);
		String actualHashes2 = idGenerator2.generateId(content2, metadata2);

		Assertions.assertNotEquals(actualHashes1, actualHashes2);

		// Assert (other expected behaviors)
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes1));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes2));
	}

	@Test
	void hashGeneratorGeneratesDifferentIdsForDifferentMetadata() {
		var idGenerator = new JdkSha256HexIdGenerator();

		final String content = "Same content";
		final Map<String, Object> metadata1 = Map.of("key", "value1");
		final Map<String, Object> metadata2 = Map.of("key", "value2");

		String hash1 = idGenerator.generateId(content, metadata1);
		String hash2 = idGenerator.generateId(content, metadata2);

		assertThat(hash1).isNotEqualTo(hash2);
	}

	@Test
	void hashGeneratorProducesValidSha256BasedUuid() {
		var idGenerator = new JdkSha256HexIdGenerator();
		final String content = "Test content";
		final Map<String, Object> metadata = Map.of("key", "value");

		String generatedId = idGenerator.generateId(content, metadata);

		// Verify it's a valid UUID
		UUID uuid = UUID.fromString(generatedId);
		assertThat(uuid).isNotNull();

		// Verify UUID format characteristics
		assertThat(generatedId).hasSize(36); // Standard UUID length with hyphens
		assertThat(generatedId.charAt(8)).isEqualTo('-');
		assertThat(generatedId.charAt(13)).isEqualTo('-');
		assertThat(generatedId.charAt(18)).isEqualTo('-');
		assertThat(generatedId.charAt(23)).isEqualTo('-');
	}

	@Test
	void hashGeneratorConsistencyAcrossMultipleCalls() {
		var idGenerator = new JdkSha256HexIdGenerator();
		final String content = "Consistency test";
		final Map<String, Object> metadata = Map.of("test", "consistency");

		// Generate ID multiple times
		String id1 = idGenerator.generateId(content, metadata);
		String id2 = idGenerator.generateId(content, metadata);
		String id3 = idGenerator.generateId(content, metadata);

		// All should be identical
		assertThat(id1).isEqualTo(id2).isEqualTo(id3);
	}

	@Test
	void hashGeneratorMetadataOrderIndependence() {
		var idGenerator = new JdkSha256HexIdGenerator();
		final String content = "Order test";

		// Create metadata with same content but different insertion order
		Map<String, Object> metadata1 = new HashMap<>();
		metadata1.put("a", "value1");
		metadata1.put("b", "value2");
		metadata1.put("c", "value3");

		Map<String, Object> metadata2 = new HashMap<>();
		metadata2.put("c", "value3");
		metadata2.put("a", "value1");
		metadata2.put("b", "value2");

		String id1 = idGenerator.generateId(content, metadata1);
		String id2 = idGenerator.generateId(content, metadata2);

		// IDs should be the same regardless of metadata insertion order
		assertThat(id1).isEqualTo(id2);
	}

	@Test
	void hashGeneratorSensitiveToMinorChanges() {
		var idGenerator = new JdkSha256HexIdGenerator();
		final Map<String, Object> metadata = Map.of("key", "value");

		// Test sensitivity to minor content changes
		String id1 = idGenerator.generateId("content", metadata);
		String id2 = idGenerator.generateId("Content", metadata); // Different case
		String id3 = idGenerator.generateId("content ", metadata); // Extra space
		String id4 = idGenerator.generateId("content\n", metadata); // Newline

		// All should be different
		assertThat(id1).isNotEqualTo(id2);
		assertThat(id1).isNotEqualTo(id3);
		assertThat(id1).isNotEqualTo(id4);
		assertThat(id2).isNotEqualTo(id3);
		assertThat(id2).isNotEqualTo(id4);
		assertThat(id3).isNotEqualTo(id4);
	}

	@Test
	void multipleGeneratorInstancesProduceSameResults() {
		final String content = "Multi-instance test";
		final Map<String, Object> metadata = Map.of("instance", "test");

		// Create multiple generator instances
		var generator1 = new JdkSha256HexIdGenerator();
		var generator2 = new JdkSha256HexIdGenerator();
		var generator3 = new JdkSha256HexIdGenerator();

		String id1 = generator1.generateId(content, metadata);
		String id2 = generator2.generateId(content, metadata);
		String id3 = generator3.generateId(content, metadata);

		// All instances should produce the same ID for the same input
		assertThat(id1).isEqualTo(id2).isEqualTo(id3);
	}

}
