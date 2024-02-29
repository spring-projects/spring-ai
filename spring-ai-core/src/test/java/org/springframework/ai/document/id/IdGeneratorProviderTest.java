/*
 * Copyright 2024-2024 the original author or authors.
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

}