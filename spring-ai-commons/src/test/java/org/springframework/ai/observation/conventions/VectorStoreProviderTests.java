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

package org.springframework.ai.observation.conventions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VectorStoreProvider}.
 *
 * @author Thomas Vitale
 */
class VectorStoreProviderTests {

	@Test
	void enumValuesShouldBeSortedAlphabetically() {
		List<String> actualNames = Arrays.stream(VectorStoreProvider.values())
			.map(Enum::name)
			.collect(Collectors.toList());

		List<String> sortedNames = actualNames.stream().sorted().collect(Collectors.toList());

		assertThat(actualNames).as("Enum values should be sorted alphabetically").isEqualTo(sortedNames);
	}

}
