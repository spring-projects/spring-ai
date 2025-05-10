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

package org.springframework.ai.observation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Unit tests for {@link ObservabilityHelper}.
 *
 * @author Jonatan Ivanov
 */
class ObservabilityHelperTests {

	@Test
	void shouldGetEmptyBracketsForEmptyMap() {
		assertThat(ObservabilityHelper.concatenateEntries(Map.of())).isEqualTo("[]");
	}

	@Test
	void shouldGetEntriesForNonEmptyMap() {
		TreeMap<String, Object> map = new TreeMap<>(Map.of("a", "1", "b", "2"));
		assertThat(ObservabilityHelper.concatenateEntries(map)).isEqualTo("[\"a\":\"1\", \"b\":\"2\"]");
	}

	@Test
	void shouldGetEmptyBracketsForEmptyList() {
		assertThat(ObservabilityHelper.concatenateStrings(List.of())).isEqualTo("[]");
	}

	@Test
	void shouldGetEntriesForNonEmptyList() {
		assertThat(ObservabilityHelper.concatenateStrings(List.of("a", "b"))).isEqualTo("[\"a\", \"b\"]");
	}

}
