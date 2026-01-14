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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Used to deserialize the `embedding` field returned by the model.
 * <p>
 * Supports two input formats:
 * <ol>
 * <li>{@code float[]} - returned directly as-is.</li>
 * <li>A Base64-encoded string representing a float array stored in little-endian format.
 * The string is first decoded into a byte array, then converted into a
 * {@code float[]}.</li>
 * </ol>
 *
 * @author Sun Yuhan
 */
public class OpenAiEmbeddingDeserializer extends ValueDeserializer<float[]> {

	@Override
	public float[] deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
			throws JacksonException {
		JsonToken token = jsonParser.currentToken();
		if (token == JsonToken.START_ARRAY) {
			return jsonParser.readValueAs(float[].class);
		}
		else if (token == JsonToken.VALUE_STRING) {
			String base64 = jsonParser.getValueAsString();
			byte[] decodedBytes = Base64.getDecoder().decode(base64);

			ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
			byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

			int floatCount = decodedBytes.length / Float.BYTES;
			float[] embeddingArray = new float[floatCount];

			for (int i = 0; i < floatCount; i++) {
				embeddingArray[i] = byteBuffer.getFloat();
			}
			return embeddingArray;
		}
		else {
			throw new StreamReadException(jsonParser, "Illegal embedding: " + token);
		}
	}

}
