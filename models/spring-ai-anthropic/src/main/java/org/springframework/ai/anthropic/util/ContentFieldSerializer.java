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

package org.springframework.ai.anthropic.util;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.springframework.ai.anthropic.api.AnthropicApi;

/**
 * Serialize the multi-type field {@link AnthropicApi.ContentBlock#content()}.
 *
 * @author Jonghoon Park
 */
public class ContentFieldSerializer extends JsonSerializer<Object> {

	@Override
	public void serialize(Object o, JsonGenerator generator, SerializerProvider serializerProvider) throws IOException {
		if (o instanceof String content) {
			generator.writeString(content);
		}
		else if (o instanceof List<?> list) {
			generator.writeStartArray();
			for (Object block : list) {
				generator.writeObject(block);
			}
			generator.writeEndArray();
		}
		else {
			generator.writeNull();
		}
	}

}
