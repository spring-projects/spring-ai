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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ThinkOption} serialization and deserialization.
 *
 * @author Mark Pollack
 */
class ThinkOptionTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testThinkBooleanEnabledSerialization() throws Exception {
		ThinkOption option = ThinkOption.ThinkBoolean.ENABLED;
		String json = this.objectMapper.writeValueAsString(option);
		assertThat(json).isEqualTo("true");
	}

	@Test
	void testThinkBooleanDisabledSerialization() throws Exception {
		ThinkOption option = ThinkOption.ThinkBoolean.DISABLED;
		String json = this.objectMapper.writeValueAsString(option);
		assertThat(json).isEqualTo("false");
	}

	@Test
	void testThinkLevelLowSerialization() throws Exception {
		ThinkOption option = ThinkOption.ThinkLevel.LOW;
		String json = this.objectMapper.writeValueAsString(option);
		assertThat(json).isEqualTo("\"low\"");
	}

	@Test
	void testThinkLevelMediumSerialization() throws Exception {
		ThinkOption option = ThinkOption.ThinkLevel.MEDIUM;
		String json = this.objectMapper.writeValueAsString(option);
		assertThat(json).isEqualTo("\"medium\"");
	}

	@Test
	void testThinkLevelHighSerialization() throws Exception {
		ThinkOption option = ThinkOption.ThinkLevel.HIGH;
		String json = this.objectMapper.writeValueAsString(option);
		assertThat(json).isEqualTo("\"high\"");
	}

	@Test
	void testDeserializeBooleanTrue() throws Exception {
		String json = "true";
		ThinkOption option = this.objectMapper.readValue(json, ThinkOption.class);
		assertThat(option).isEqualTo(ThinkOption.ThinkBoolean.ENABLED);
		assertThat(option).isInstanceOf(ThinkOption.ThinkBoolean.class);
		assertThat(((ThinkOption.ThinkBoolean) option).enabled()).isTrue();
	}

	@Test
	void testDeserializeBooleanFalse() throws Exception {
		String json = "false";
		ThinkOption option = this.objectMapper.readValue(json, ThinkOption.class);
		assertThat(option).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
		assertThat(option).isInstanceOf(ThinkOption.ThinkBoolean.class);
		assertThat(((ThinkOption.ThinkBoolean) option).enabled()).isFalse();
	}

	@Test
	void testDeserializeStringLow() throws Exception {
		String json = "\"low\"";
		ThinkOption option = this.objectMapper.readValue(json, ThinkOption.class);
		assertThat(option).isInstanceOf(ThinkOption.ThinkLevel.class);
		assertThat(((ThinkOption.ThinkLevel) option).level()).isEqualTo("low");
	}

	@Test
	void testDeserializeStringMedium() throws Exception {
		String json = "\"medium\"";
		ThinkOption option = this.objectMapper.readValue(json, ThinkOption.class);
		assertThat(option).isInstanceOf(ThinkOption.ThinkLevel.class);
		assertThat(((ThinkOption.ThinkLevel) option).level()).isEqualTo("medium");
	}

	@Test
	void testDeserializeStringHigh() throws Exception {
		String json = "\"high\"";
		ThinkOption option = this.objectMapper.readValue(json, ThinkOption.class);
		assertThat(option).isInstanceOf(ThinkOption.ThinkLevel.class);
		assertThat(((ThinkOption.ThinkLevel) option).level()).isEqualTo("high");
	}

	@Test
	void testDeserializeNull() throws Exception {
		String json = "null";
		ThinkOption option = this.objectMapper.readValue(json, ThinkOption.class);
		assertThat(option).isNull();
	}

	@Test
	void testThinkLevelInvalidStringThrowsException() {
		assertThatThrownBy(() -> new ThinkOption.ThinkLevel("invalid")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("think level must be one of [low, medium, high], got: invalid");
	}

	@Test
	void testThinkLevelConstants() {
		assertThat(ThinkOption.ThinkLevel.LOW.level()).isEqualTo("low");
		assertThat(ThinkOption.ThinkLevel.MEDIUM.level()).isEqualTo("medium");
		assertThat(ThinkOption.ThinkLevel.HIGH.level()).isEqualTo("high");
	}

	@Test
	void testThinkBooleanConstants() {
		assertThat(ThinkOption.ThinkBoolean.ENABLED.enabled()).isTrue();
		assertThat(ThinkOption.ThinkBoolean.DISABLED.enabled()).isFalse();
	}

	@Test
	void testToJsonValue() {
		assertThat(ThinkOption.ThinkBoolean.ENABLED.toJsonValue()).isEqualTo(true);
		assertThat(ThinkOption.ThinkBoolean.DISABLED.toJsonValue()).isEqualTo(false);
		assertThat(ThinkOption.ThinkLevel.LOW.toJsonValue()).isEqualTo("low");
		assertThat(ThinkOption.ThinkLevel.MEDIUM.toJsonValue()).isEqualTo("medium");
		assertThat(ThinkOption.ThinkLevel.HIGH.toJsonValue()).isEqualTo("high");
	}

}
