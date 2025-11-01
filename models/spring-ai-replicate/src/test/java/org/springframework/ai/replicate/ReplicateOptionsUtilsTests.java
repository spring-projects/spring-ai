/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.replicate;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReplicateOptionsUtils}.
 *
 * @author Rene Maierhofer
 */
class ReplicateOptionsUtilsTests {

	@Test
	void testConvertValueWithBoolean() {
		assertThat(ReplicateOptionsUtils.convertValue("true")).isInstanceOf(Boolean.class).isEqualTo(true);
		assertThat(ReplicateOptionsUtils.convertValue("false")).isInstanceOf(Boolean.class).isEqualTo(false);
		assertThat(ReplicateOptionsUtils.convertValue("TRUE")).isEqualTo(true);
		assertThat(ReplicateOptionsUtils.convertValue("False")).isEqualTo(false);
		assertThat(ReplicateOptionsUtils.convertValue("TrUe")).isEqualTo(true);
	}

	@Test
	void testConvertValueNumeric() {
		assertThat(ReplicateOptionsUtils.convertValue("42")).isInstanceOf(Integer.class).isEqualTo(42);
		assertThat(ReplicateOptionsUtils.convertValue("3.14")).isInstanceOf(Double.class).isEqualTo(3.14);
		assertThat(ReplicateOptionsUtils.convertValue("1.5e10")).isInstanceOf(Double.class).isEqualTo(1.5E10);
	}

	@Test
	void testConvertValueWithPlainString() {
		assertThat(ReplicateOptionsUtils.convertValue("hello world")).isInstanceOf(String.class)
			.isEqualTo("hello world");
	}

	@Test
	void testConvertValueWithNonStringValue() {
		Integer intValue = 100;
		Object result = ReplicateOptionsUtils.convertValue(intValue);
		assertThat(result).isSameAs(intValue);

		Double doubleValue = 5.5;
		result = ReplicateOptionsUtils.convertValue(doubleValue);
		assertThat(result).isSameAs(doubleValue);

		Boolean boolValue = true;
		result = ReplicateOptionsUtils.convertValue(boolValue);
		assertThat(result).isSameAs(boolValue);
	}

	@Test
	void testConvertMapValuesWithMixedTypes() {
		Map<String, Object> source = new HashMap<>();
		source.put("temperature", "0.7");
		source.put("maxTokens", "100");
		source.put("enabled", "true");
		source.put("model", "meta/llama-3");
		source.put("existingInt", 42);

		Map<String, Object> result = ReplicateOptionsUtils.convertMapValues(source);

		assertThat(result).hasSize(5);
		assertThat(result.get("temperature")).isInstanceOf(Double.class).isEqualTo(0.7);
		assertThat(result.get("maxTokens")).isInstanceOf(Integer.class).isEqualTo(100);
		assertThat(result.get("enabled")).isInstanceOf(Boolean.class).isEqualTo(true);
		assertThat(result.get("model")).isInstanceOf(String.class).isEqualTo("meta/llama-3");
		assertThat(result.get("existingInt")).isInstanceOf(Integer.class).isEqualTo(42);
	}

}
