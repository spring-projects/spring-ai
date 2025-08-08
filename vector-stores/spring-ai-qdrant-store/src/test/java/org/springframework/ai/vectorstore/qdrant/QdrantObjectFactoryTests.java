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

package org.springframework.ai.vectorstore.qdrant;

import java.util.Map;

import io.qdrant.client.grpc.JsonWithInt.NullValue;
import io.qdrant.client.grpc.JsonWithInt.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link QdrantObjectFactory}.
 *
 * @author Heonwoo Kim
 */

class QdrantObjectFactoryTests {

	@Test
	void toObjectMapShouldHandleNullValues() {
		Map<String, Value> payloadWithNull = Map.of("name", Value.newBuilder().setStringValue("Spring AI").build(),
				"version", Value.newBuilder().setDoubleValue(1.0).build(), "is_ga",
				Value.newBuilder().setBoolValue(true).build(), "description",
				Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payloadWithNull);

		assertThat(result).isNotNull();
		assertThat(result).hasSize(4);
		assertThat(result.get("name")).isEqualTo("Spring AI");
		assertThat(result.get("version")).isEqualTo(1.0);
		assertThat(result.get("is_ga")).isEqualTo(true);
		assertThat(result).containsKey("description");
		assertThat(result.get("description")).isNull();
	}

	@Test
	void toObjectMapShouldHandleEmptyMap() {
		Map<String, Value> emptyPayload = Map.of();

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(emptyPayload);

		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
	}

	@Test
	void toObjectMapShouldHandleAllPrimitiveTypes() {
		Map<String, Value> payload = Map.of("stringField", Value.newBuilder().setStringValue("test").build(),
				"intField", Value.newBuilder().setIntegerValue(1).build(), "doubleField",
				Value.newBuilder().setDoubleValue(1.1).build(), "boolField",
				Value.newBuilder().setBoolValue(false).build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(4);
		assertThat(result.get("stringField")).isEqualTo("test");
		assertThat(result.get("intField")).isEqualTo(1L);
		assertThat(result.get("doubleField")).isEqualTo(1.1);
		assertThat(result.get("boolField")).isEqualTo(false);
	}

	@Test
	void toObjectMapShouldHandleKindNotSetValue() {
		// This test verifies that KIND_NOT_SET values are handled gracefully
		Value kindNotSetValue = Value.newBuilder().build(); // Default case - KIND_NOT_SET

		Map<String, Value> payload = Map.of("unsetField", kindNotSetValue);

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(1);
		assertThat(result.get("unsetField")).isNull();
	}

	@Test
	void toObjectMapShouldThrowExceptionForNullPayload() {
		assertThatThrownBy(() -> QdrantObjectFactory.toObjectMap(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Payload map must not be null");
	}

	@Test
	void toObjectMapShouldHandleMixedDataTypes() {
		Map<String, Value> payload = Map.of("text", Value.newBuilder().setStringValue("").build(), // empty
																									// string
				"flag", Value.newBuilder().setBoolValue(true).build(), "nullField",
				Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(), "number",
				Value.newBuilder().setIntegerValue(1).build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(4);
		assertThat(result.get("text")).isEqualTo("");
		assertThat(result.get("flag")).isEqualTo(true);
		assertThat(result.get("nullField")).isNull();
		assertThat(result.get("number")).isEqualTo(1L);
	}

}
