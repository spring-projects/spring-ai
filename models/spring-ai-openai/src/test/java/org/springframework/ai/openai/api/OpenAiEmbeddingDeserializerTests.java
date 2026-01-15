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

package org.springframework.ai.openai.api;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiEmbeddingDeserializer}
 *
 * @author Sun Yuhan
 */
class OpenAiEmbeddingDeserializerTests {

	private final OpenAiEmbeddingDeserializer deserializer = new OpenAiEmbeddingDeserializer();

	@Test
	void testDeserializeFloatArray() {
		JsonParser parser = mock(JsonParser.class);
		DeserializationContext context = mock(DeserializationContext.class);

		when(parser.currentToken()).thenReturn(JsonToken.START_ARRAY);
		float[] expected = new float[] { 1.0f, 2.0f, 3.0f };
		when(parser.readValueAs(float[].class)).thenReturn(expected);

		float[] result = this.deserializer.deserialize(parser, context);
		assertArrayEquals(expected, result);
	}

	@Test
	void testDeserializeBase64String() {
		float[] original = new float[] { 4.2f, -1.5f, 0.0f };
		ByteBuffer buffer = ByteBuffer.allocate(original.length * Float.BYTES);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		for (float v : original) {
			buffer.putFloat(v);
		}
		String base64 = Base64.getEncoder().encodeToString(buffer.array());

		JsonParser parser = mock(JsonParser.class);
		DeserializationContext context = mock(DeserializationContext.class);

		when(parser.currentToken()).thenReturn(JsonToken.VALUE_STRING);
		when(parser.getValueAsString()).thenReturn(base64);

		float[] result = this.deserializer.deserialize(parser, context);

		assertArrayEquals(original, result, 0.0001f);
	}

	@Test
	void testDeserializeIllegalToken() {
		JsonParser parser = mock(JsonParser.class);
		DeserializationContext context = mock(DeserializationContext.class);

		when(parser.currentToken()).thenReturn(JsonToken.VALUE_NUMBER_INT);

		JacksonException e = assertThrows(JacksonException.class, () -> this.deserializer.deserialize(parser, context));
		assertTrue(e.getMessage().contains("Illegal embedding"));
	}

	@Test
	void testDeserializeEmbeddingWithFloatArray() {
		String json = """
				{
					"index": 1,
					"embedding": [1.0, 2.0, 3.0],
					"object": "embedding"
				}
				""";
		OpenAiApi.Embedding embedding = JsonMapper.shared().readValue(json, OpenAiApi.Embedding.class);
		assertEquals(1, embedding.index());
		assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f }, embedding.embedding(), 0.0001f);
		assertEquals("embedding", embedding.object());
	}

	@Test
	void testDeserializeEmbeddingWithBase64String() {
		float[] original = new float[] { 4.2f, -1.5f, 0.0f };
		ByteBuffer buffer = ByteBuffer.allocate(original.length * Float.BYTES);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		for (float v : original) {
			buffer.putFloat(v);
		}
		String base64 = Base64.getEncoder().encodeToString(buffer.array());

		String json = """
				{
					"index": 2,
					"embedding": "%s",
					"object": "embedding"
				}
				""".formatted(base64);

		OpenAiApi.Embedding embedding = JsonMapper.shared().readValue(json, OpenAiApi.Embedding.class);
		assertEquals(2, embedding.index());
		assertArrayEquals(original, embedding.embedding(), 0.0001f);
		assertEquals("embedding", embedding.object());
	}

	@Test
	void testDeserializeEmbeddingWithWrongType() {
		String json = """
				{
					"index": 3,
					"embedding": 123,
					"object": "embedding"
				}
				""";
		JacksonException ex = assertThrows(JacksonException.class,
				() -> JsonMapper.shared().readValue(json, OpenAiApi.Embedding.class));
		assertTrue(ex.getMessage().contains("Illegal embedding"));
	}

}
