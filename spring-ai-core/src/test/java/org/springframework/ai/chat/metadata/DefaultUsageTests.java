/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DefaultUsageTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testSerializationWithAllFields() throws Exception {
		DefaultUsage usage = new DefaultUsage(100L, 50L, 150L);
		String json = objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":100,\"generationTokens\":50,\"totalTokens\":150}", json);
	}

	@Test
	void testDeserializationWithAllFields() throws Exception {
		String json = "{\"promptTokens\":100,\"generationTokens\":50,\"totalTokens\":150}";
		DefaultUsage usage = objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(100L, usage.getPromptTokens());
		assertEquals(50L, usage.getGenerationTokens());
		assertEquals(150L, usage.getTotalTokens());
	}

	@Test
	void testSerializationWithNullFields() throws Exception {
		DefaultUsage usage = new DefaultUsage(null, null, null);
		String json = objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":0,\"generationTokens\":0,\"totalTokens\":0}", json);
	}

	@Test
	void testDeserializationWithMissingFields() throws Exception {
		String json = "{\"promptTokens\":100}";
		DefaultUsage usage = objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(100L, usage.getPromptTokens());
		assertEquals(0L, usage.getGenerationTokens());
		assertEquals(100L, usage.getTotalTokens());
	}

	@Test
	void testDeserializationWithNullFields() throws Exception {
		String json = "{\"promptTokens\":null,\"generationTokens\":null,\"totalTokens\":null}";
		DefaultUsage usage = objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(0L, usage.getPromptTokens());
		assertEquals(0L, usage.getGenerationTokens());
		assertEquals(0L, usage.getTotalTokens());
	}

	@Test
	void testRoundTripSerialization() throws Exception {
		DefaultUsage original = new DefaultUsage(100L, 50L, 150L);
		String json = objectMapper.writeValueAsString(original);
		DefaultUsage deserialized = objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(original.getPromptTokens(), deserialized.getPromptTokens());
		assertEquals(original.getGenerationTokens(), deserialized.getGenerationTokens());
		assertEquals(original.getTotalTokens(), deserialized.getTotalTokens());
	}

	@Test
	void testTwoArgumentConstructorAndSerialization() throws Exception {
		DefaultUsage usage = new DefaultUsage(100L, 50L);

		// Test that the fields are set correctly
		assertEquals(100L, usage.getPromptTokens());
		assertEquals(50L, usage.getGenerationTokens());
		assertEquals(150L, usage.getTotalTokens()); // 100 + 50 = 150

		// Test serialization
		String json = objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":100,\"generationTokens\":50,\"totalTokens\":150}", json);

		// Test deserialization
		DefaultUsage deserializedUsage = objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(100L, deserializedUsage.getPromptTokens());
		assertEquals(50L, deserializedUsage.getGenerationTokens());
		assertEquals(150L, deserializedUsage.getTotalTokens());
	}

	@Test
	void testTwoArgumentConstructorWithNullValues() throws Exception {
		DefaultUsage usage = new DefaultUsage(null, null);

		// Test that null values are converted to 0
		assertEquals(0L, usage.getPromptTokens());
		assertEquals(0L, usage.getGenerationTokens());
		assertEquals(0L, usage.getTotalTokens());

		// Test serialization
		String json = objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":0,\"generationTokens\":0,\"totalTokens\":0}", json);

		// Test deserialization
		DefaultUsage deserializedUsage = objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(0L, deserializedUsage.getPromptTokens());
		assertEquals(0L, deserializedUsage.getGenerationTokens());
		assertEquals(0L, deserializedUsage.getTotalTokens());
	}

}
