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

package org.springframework.ai.ollama.api;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jspecify.annotations.Nullable;

/**
 * Represents the thinking option for Ollama models. The think option controls whether
 * models emit their reasoning trace before the final answer.
 * <p>
 * Most models (Qwen 3, DeepSeek-v3.1, DeepSeek R1) accept boolean enable/disable. The
 * GPT-OSS model requires string levels: "low", "medium", or "high".
 *
 * @author Mark Pollack
 * @since 1.1.0
 * @see ThinkBoolean
 * @see ThinkLevel
 */
@JsonSerialize(using = ThinkOption.ThinkOptionSerializer.class)
@JsonDeserialize(using = ThinkOption.ThinkOptionDeserializer.class)
public sealed interface ThinkOption {

	/**
	 * Converts this think option to its JSON representation.
	 * @return the JSON value (Boolean or String)
	 */
	Object toJsonValue();

	/**
	 * Serializer that writes ThinkOption as raw boolean or string values.
	 */
	class ThinkOptionSerializer extends JsonSerializer<ThinkOption> {

		@Override
		public void serialize(ThinkOption value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			if (value == null) {
				gen.writeNull();
			}
			else {
				gen.writeObject(value.toJsonValue());
			}
		}

	}

	/**
	 * Deserializer that reads boolean or string values into ThinkOption instances.
	 */
	class ThinkOptionDeserializer extends JsonDeserializer<ThinkOption> {

		@Override
		public @Nullable ThinkOption deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			JsonToken token = p.currentToken();
			if (token == JsonToken.VALUE_TRUE) {
				return ThinkBoolean.ENABLED;
			}
			else if (token == JsonToken.VALUE_FALSE) {
				return ThinkBoolean.DISABLED;
			}
			else if (token == JsonToken.VALUE_STRING) {
				return new ThinkLevel(p.getValueAsString());
			}
			else if (token == JsonToken.VALUE_NULL) {
				return null;
			}
			throw new IOException("Cannot deserialize ThinkOption from token: " + token);
		}

	}

	/**
	 * Boolean-style think option for models that support simple enable/disable. Supported
	 * by Qwen 3, DeepSeek-v3.1, and DeepSeek R1 models.
	 *
	 * @param enabled whether thinking is enabled
	 */
	record ThinkBoolean(boolean enabled) implements ThinkOption {

		/**
		 * Constant for enabled thinking.
		 */
		public static final ThinkBoolean ENABLED = new ThinkBoolean(true);

		/**
		 * Constant for disabled thinking.
		 */
		public static final ThinkBoolean DISABLED = new ThinkBoolean(false);

		@Override
		public Object toJsonValue() {
			return this.enabled;
		}

	}

	/**
	 * String-level think option for the GPT-OSS model which requires explicit levels.
	 *
	 * @param level the thinking level: "low", "medium", or "high"
	 */
	record ThinkLevel(String level) implements ThinkOption {

		private static final List<String> VALID_LEVELS = List.of("low", "medium", "high");

		/**
		 * Low thinking level for GPT-OSS.
		 */
		public static final ThinkLevel LOW = new ThinkLevel("low");

		/**
		 * Medium thinking level for GPT-OSS.
		 */
		public static final ThinkLevel MEDIUM = new ThinkLevel("medium");

		/**
		 * High thinking level for GPT-OSS.
		 */
		public static final ThinkLevel HIGH = new ThinkLevel("high");

		/**
		 * models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/ThinkOption.java
		 * Creates a new ThinkLevel with validation.
		 */
		public ThinkLevel {
			if (level != null && !VALID_LEVELS.contains(level)) {
				throw new IllegalArgumentException("think level must be one of " + VALID_LEVELS + ", got: " + level);
			}
		}

		@Override
		public Object toJsonValue() {
			return this.level;
		}

	}

}
