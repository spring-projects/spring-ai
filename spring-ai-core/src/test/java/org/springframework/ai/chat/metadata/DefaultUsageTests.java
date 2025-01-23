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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultUsageTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testSerializationWithAllFields() throws Exception {
		DefaultUsage usage = new DefaultUsage(100, 50, 150);
		String json = this.objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150,\"generationTokens\":50}",
				json);
	}

	@Test
	void testDeserializationWithAllFields() throws Exception {
		String json = "{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150,\"generationTokens\":50}";
		DefaultUsage usage = this.objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(100, usage.getPromptTokens());
		assertEquals(50, usage.getCompletionTokens());
		assertEquals(150, usage.getTotalTokens());
	}

	@Test
	void testSerializationWithNullFields() throws Exception {
		DefaultUsage usage = new DefaultUsage(null, null, null);
		String json = this.objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":0,\"completionTokens\":0,\"totalTokens\":0,\"generationTokens\":0}", json);
	}

	@Test
	void testDeserializationWithMissingFields() throws Exception {
		String json = "{\"promptTokens\":100}";
		DefaultUsage usage = this.objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(100, usage.getPromptTokens());
		assertEquals(0, usage.getCompletionTokens());
		assertEquals(100, usage.getTotalTokens());
	}

	@Test
	void testDeserializationWithNullFields() throws Exception {
		String json = "{\"promptTokens\":null,\"completionTokens\":null,\"totalTokens\":null}";
		DefaultUsage usage = this.objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(0, usage.getPromptTokens());
		assertEquals(0, usage.getCompletionTokens());
		assertEquals(0, usage.getTotalTokens());
	}

	@Test
	void testRoundTripSerialization() throws Exception {
		DefaultUsage original = new DefaultUsage(100, 50, 150);
		String json = this.objectMapper.writeValueAsString(original);
		DefaultUsage deserialized = this.objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(original.getPromptTokens(), deserialized.getPromptTokens());
		assertEquals(original.getCompletionTokens(), deserialized.getCompletionTokens());
		assertEquals(original.getTotalTokens(), deserialized.getTotalTokens());
	}

	@Test
	void testTwoArgumentConstructorAndSerialization() throws Exception {
		DefaultUsage usage = new DefaultUsage(100, 50);

		// Test that the fields are set correctly
		assertEquals(100, usage.getPromptTokens());
		assertEquals(50, usage.getCompletionTokens());
		assertEquals(150, usage.getTotalTokens()); // 100 + 50 = 150

		// Test serialization
		String json = this.objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":100,\"completionTokens\":50,\"totalTokens\":150,\"generationTokens\":50}",
				json);

		// Test deserialization
		DefaultUsage deserializedUsage = this.objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(100, deserializedUsage.getPromptTokens());
		assertEquals(50, deserializedUsage.getCompletionTokens());
		assertEquals(150, deserializedUsage.getTotalTokens());
	}

	@Test
	void testTwoArgumentConstructorWithNullValues() throws Exception {
		DefaultUsage usage = new DefaultUsage(null, null);

		// Test that null values are converted to 0
		assertEquals(0, usage.getPromptTokens());
		assertEquals(0, usage.getCompletionTokens());
		assertEquals(0, usage.getTotalTokens());

		// Test serialization
		String json = this.objectMapper.writeValueAsString(usage);
		assertEquals("{\"promptTokens\":0,\"completionTokens\":0,\"totalTokens\":0,\"generationTokens\":0}", json);

		// Test deserialization
		DefaultUsage deserializedUsage = this.objectMapper.readValue(json, DefaultUsage.class);
		assertEquals(0, deserializedUsage.getPromptTokens());
		assertEquals(0, deserializedUsage.getCompletionTokens());
		assertEquals(0, deserializedUsage.getTotalTokens());
	}

}
