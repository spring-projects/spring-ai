/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MapUtils}.
 *
 * @author Evan Yao
 * @since 1.0.0
 */
class MapUtilsTests {

	@Test
	void unwrapPresentOptional() {
		Map<String, Object> map = Map.of("key", Optional.of("value"));
		Map<String, Object> result = MapUtils.unwrapOptionals(map);
		assertThat(result).containsEntry("key", "value");
	}

	@Test
	void unwrapEmptyOptional() {
		Map<String, Object> map = Map.of("key", Optional.empty());
		Map<String, Object> result = MapUtils.unwrapOptionals(map);
		assertThat(result).containsEntry("key", null);
	}

	@Test
	void nonOptionalValuesPreserved() {
		Map<String, Object> map = Map.of("str", "hello", "num", 42, "bool", true);
		Map<String, Object> result = MapUtils.unwrapOptionals(map);
		assertThat(result).containsEntry("str", "hello");
		assertThat(result).containsEntry("num", 42);
		assertThat(result).containsEntry("bool", true);
	}

	@Test
	void mixedOptionalAndNonOptional() {
		Map<String, Object> map = new HashMap<>();
		map.put("present", Optional.of("yes"));
		map.put("empty", Optional.empty());
		map.put("plain", "no");
		Map<String, Object> result = MapUtils.unwrapOptionals(map);
		assertThat(result).containsEntry("present", "yes");
		assertThat(result).containsEntry("empty", null);
		assertThat(result).containsEntry("plain", "no");
	}

	@Test
	void emptyMapReturnsEmptyMap() {
		Map<String, Object> result = MapUtils.unwrapOptionals(Map.of());
		assertThat(result).isEmpty();
	}

	@Test
	void returnsNewMapInstance() {
		Map<String, Object> original = Map.of("key", "value");
		Map<String, Object> result = MapUtils.unwrapOptionals(original);
		assertThat(result).isNotSameAs(original);
	}

}
