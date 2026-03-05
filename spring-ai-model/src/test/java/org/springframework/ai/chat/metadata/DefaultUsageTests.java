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

package org.springframework.ai.chat.metadata;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultUsageTests {

	@Test
	void testSerializationWithAllFields() throws Exception {
		DefaultUsage usage = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150));
		String json = JsonMapper.shared().writeValueAsString(usage);
		assertThat(json).isEqualTo("{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150}");
	}

	@Test
	void testDeserializationWithAllFields() throws Exception {
		String json = "{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150}";
		DefaultUsage usage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150);
	}

	@Test
	void testSerializationWithNullFields() throws Exception {
		DefaultUsage usage = new DefaultUsage((Integer) null, (Integer) null, (Integer) null);
		String json = JsonMapper.shared().writeValueAsString(usage);
		assertThat(json).isEqualTo("{\"promptTokens\":0,\"completionTokens\":0,\"totalTokens\":0}");
	}

	@Test
	void testDeserializationWithMissingFields() throws Exception {
		String json = "{\"promptTokens\":100}";
		DefaultUsage usage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(0);
		assertThat(usage.getTotalTokens()).isEqualTo(100);
	}

	@Test
	void testDeserializationWithNullFields() throws Exception {
		String json = "{\"promptTokens\":null,\"completionTokens\":null,\"totalTokens\":null}";
		DefaultUsage usage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(usage.getPromptTokens()).isEqualTo(0);
		assertThat(usage.getCompletionTokens()).isEqualTo(0);
		assertThat(usage.getTotalTokens()).isEqualTo(0);
	}

	@Test
	void testRoundTripSerialization() throws Exception {
		DefaultUsage original = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150));
		String json = JsonMapper.shared().writeValueAsString(original);
		DefaultUsage deserialized = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(deserialized.getPromptTokens()).isEqualTo(original.getPromptTokens());
		assertThat(deserialized.getCompletionTokens()).isEqualTo(original.getCompletionTokens());
		assertThat(deserialized.getTotalTokens()).isEqualTo(original.getTotalTokens());
	}

	@Test
	void testTwoArgumentConstructorAndSerialization() throws Exception {
		DefaultUsage usage = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50));

		// Test that the fields are set correctly
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150); // 100 + 50 = 150

		// Test serialization
		String json = JsonMapper.shared().writeValueAsString(usage);
		assertThat(json).isEqualTo("{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150}");

		// Test deserialization
		DefaultUsage deserializedUsage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(deserializedUsage.getPromptTokens()).isEqualTo(100);
		assertThat(deserializedUsage.getCompletionTokens()).isEqualTo(50);
		assertThat(deserializedUsage.getTotalTokens()).isEqualTo(150);
	}

	@Test
	void testTwoArgumentConstructorWithNullValues() throws Exception {
		DefaultUsage usage = new DefaultUsage((Integer) null, (Integer) null);

		// Test that null values are converted to 0
		assertThat(usage.getPromptTokens()).isEqualTo(0);
		assertThat(usage.getCompletionTokens()).isEqualTo(0);
		assertThat(usage.getTotalTokens()).isEqualTo(0);

		// Test serialization
		String json = JsonMapper.shared().writeValueAsString(usage);
		assertThat(json).isEqualTo("{\"promptTokens\":0,\"completionTokens\":0,\"totalTokens\":0}");

		// Test deserialization
		DefaultUsage deserializedUsage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(deserializedUsage.getPromptTokens()).isEqualTo(0);
		assertThat(deserializedUsage.getCompletionTokens()).isEqualTo(0);
		assertThat(deserializedUsage.getTotalTokens()).isEqualTo(0);
	}

	@Test
	void testDeserializationWithDifferentPropertyOrder() throws Exception {
		String json = "{\"totalTokens\":150,\"completionTokens\":50,\"promptTokens\":100}";
		DefaultUsage usage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150);
	}

	@Test
	void testSerializationWithCustomNativeUsage() throws Exception {
		Map<String, Object> customNativeUsage = new HashMap<>();
		customNativeUsage.put("custom_field", "custom_value");
		customNativeUsage.put("custom_number", 42);

		DefaultUsage usage = new DefaultUsage(100, 50, 150, customNativeUsage);
		String json = JsonMapper.shared().writeValueAsString(usage);
		assertThat(json).isEqualTo(
				"{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150,\"nativeUsage\":{\"custom_field\":\"custom_value\",\"custom_number\":42}}");
	}

	@Test
	void testDeserializationWithCustomNativeUsage() throws Exception {
		String json = "{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150,\"nativeUsage\":{\"custom_field\":\"custom_value\",\"custom_number\":42}}";
		DefaultUsage usage = JsonMapper.shared().readValue(json, DefaultUsage.class);
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150);

		@SuppressWarnings("unchecked")
		Map<String, Object> nativeUsage = (Map<String, Object>) usage.getNativeUsage();
		assertThat(nativeUsage.get("custom_field")).isEqualTo("custom_value");
		assertThat(nativeUsage.get("custom_number")).isEqualTo(42);
	}

	@Test
	void testArbitraryNativeUsageMap() throws Exception {
		Map<String, Object> arbitraryMap = new HashMap<>();
		arbitraryMap.put("field1", "value1");
		arbitraryMap.put("field2", 42);
		arbitraryMap.put("field3", true);
		arbitraryMap.put("field4", java.util.Arrays.asList(1, 2, 3));
		arbitraryMap.put("field5", java.util.Map.of("nested", "value"));

		DefaultUsage usage = new DefaultUsage(100, 50, 150, arbitraryMap);

		String json = JsonMapper.shared().writeValueAsString(usage);
		DefaultUsage deserialized = JsonMapper.shared().readValue(json, DefaultUsage.class);

		assertThat(deserialized.getPromptTokens()).isEqualTo(usage.getPromptTokens());
		assertThat(deserialized.getCompletionTokens()).isEqualTo(usage.getCompletionTokens());
		assertThat(deserialized.getTotalTokens()).isEqualTo(usage.getTotalTokens());
		assertThat(deserialized.getCompletionTokens()).isEqualTo(usage.getCompletionTokens());

		@SuppressWarnings("unchecked")
		Map<String, Object> deserializedMap = (Map<String, Object>) deserialized.getNativeUsage();
		assertThat(deserializedMap.get("field1")).isEqualTo("value1");
		assertThat(deserializedMap.get("field2")).isEqualTo(42);
		assertThat(deserializedMap.get("field3")).isEqualTo(true);
		assertThat(deserializedMap.get("field4")).isEqualTo(java.util.Arrays.asList(1, 2, 3));
		assertThat(deserializedMap.get("field5")).isEqualTo(java.util.Map.of("nested", "value"));
	}

	@Test
	@SuppressWarnings("SelfAssertion")
	void testEqualsAndHashCode() {
		DefaultUsage usage1 = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150));
		DefaultUsage usage2 = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150));
		DefaultUsage usage3 = new DefaultUsage(Integer.valueOf(200), Integer.valueOf(100), Integer.valueOf(300));
		DefaultUsage usage4 = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150),
				Map.of("custom", "value"));

		// Test equals
		assertThat(usage1).isEqualTo(usage2);
		assertThat(usage1).isNotEqualTo(usage3);
		assertThat(usage1).isNotEqualTo(usage4);
		assertThat(usage1).isNotEqualTo(null);
		assertThat(usage1).isNotEqualTo(new Object());

		// Test hashCode
		assertThat(usage1).hasSameHashCodeAs(usage2);
		assertThat(usage1.hashCode()).isNotEqualTo(usage3.hashCode());
		assertThat(usage1.hashCode()).isNotEqualTo(usage4.hashCode());

		// Test reflexivity
		assertThat(usage1).isEqualTo(usage1);
		assertThat(usage1).hasSameHashCodeAs(usage1);

		// Test symmetry
		assertThat(usage1.equals(usage2)).isEqualTo(usage2.equals(usage1));

		// Test with different nativeUsage
		DefaultUsage usage5 = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150),
				Map.of("key", "value"));
		DefaultUsage usage6 = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150),
				Map.of("key", "value"));
		assertThat(usage5).isEqualTo(usage6);
		assertThat(usage5).hasSameHashCodeAs(usage6);
	}

	@Test
	void testToString() {
		DefaultUsage usage = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150));
		assertThat(usage).hasToString("DefaultUsage{promptTokens=100, completionTokens=50, totalTokens=150}");

		// Test with custom nativeUsage
		DefaultUsage usageWithNative = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), Integer.valueOf(150),
				Map.of("custom", "value"));
		assertThat(usageWithNative).hasToString("DefaultUsage{promptTokens=100, completionTokens=50, totalTokens=150}");

		// Test with null values
		DefaultUsage usageWithNulls = new DefaultUsage(null, null, null);
		assertThat(usageWithNulls).hasToString("DefaultUsage{promptTokens=0, completionTokens=0, totalTokens=0}");
	}

	@Test
	void testNegativeTokenValues() throws Exception {
		DefaultUsage usage = new DefaultUsage(Integer.valueOf(-1), Integer.valueOf(-2), Integer.valueOf(-3));
		assertThat(usage.getPromptTokens()).isEqualTo(-1);
		assertThat(usage.getCompletionTokens()).isEqualTo(-2);
		assertThat(usage.getTotalTokens()).isEqualTo(-3);

		String json = JsonMapper.shared().writeValueAsString(usage);
		assertThat(json).isEqualTo("{\"promptTokens\":-1,\"completionTokens\":-2,\"totalTokens\":-3}");
	}

	@Test
	void testCalculatedTotalTokens() {
		// Test when total tokens is null and should be calculated
		DefaultUsage usage = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50), null);
		assertThat(usage.getTotalTokens()).isEqualTo(150); // Should be sum of prompt and
															// completion tokens

		// Test that explicit total tokens takes precedence over calculated
		DefaultUsage usageWithExplicitTotal = new DefaultUsage(Integer.valueOf(100), Integer.valueOf(50),
				Integer.valueOf(200));
		assertThat(usageWithExplicitTotal.getTotalTokens()).isEqualTo(200); // Should use
																			// explicit
																			// value
	}

}
