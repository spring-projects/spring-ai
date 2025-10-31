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
 * ignore: test 10 for github workflow trigger on commit.
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

	@Test
	void toObjectMapShouldHandleWhitespaceStrings() {
		Map<String, Value> payload = Map.of("spaces", Value.newBuilder().setStringValue("   ").build(), "tabs",
				Value.newBuilder().setStringValue("\t\t").build(), "newlines",
				Value.newBuilder().setStringValue("\n\r\n").build(), "mixed",
				Value.newBuilder().setStringValue(" \t\n mixed \r\n ").build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(4);
		assertThat(result.get("spaces")).isEqualTo("   ");
		assertThat(result.get("tabs")).isEqualTo("\t\t");
		assertThat(result.get("newlines")).isEqualTo("\n\r\n");
		assertThat(result.get("mixed")).isEqualTo(" \t\n mixed \r\n ");
	}

	@Test
	void toObjectMapShouldHandleComplexFieldNames() {
		Map<String, Value> payload = Map.of("field_with_underscores",
				Value.newBuilder().setStringValue("value1").build(), "field-with-dashes",
				Value.newBuilder().setStringValue("value2").build(), "field.with.dots",
				Value.newBuilder().setStringValue("value3").build(), "FIELD_WITH_CAPS",
				Value.newBuilder().setStringValue("value4").build(), "field1",
				Value.newBuilder().setStringValue("value5").build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(5);
		assertThat(result.get("field_with_underscores")).isEqualTo("value1");
		assertThat(result.get("field-with-dashes")).isEqualTo("value2");
		assertThat(result.get("field.with.dots")).isEqualTo("value3");
		assertThat(result.get("FIELD_WITH_CAPS")).isEqualTo("value4");
		assertThat(result.get("field1")).isEqualTo("value5");
	}

	@Test
	void toObjectMapShouldHandleSingleCharacterValues() {
		Map<String, Value> payload = Map.of("singleChar", Value.newBuilder().setStringValue("a").build(), "specialChar",
				Value.newBuilder().setStringValue("@").build(), "digit",
				Value.newBuilder().setStringValue("1").build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(3);
		assertThat(result.get("singleChar")).isEqualTo("a");
		assertThat(result.get("specialChar")).isEqualTo("@");
		assertThat(result.get("digit")).isEqualTo("1");
	}

	@Test
	void toObjectMapShouldHandleAllNullValues() {
		Map<String, Value> payload = Map.of("null1", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(),
				"null2", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(), "null3",
				Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(3);
		assertThat(result.get("null1")).isNull();
		assertThat(result.get("null2")).isNull();
		assertThat(result.get("null3")).isNull();
		assertThat(result).containsKeys("null1", "null2", "null3");
	}

	@Test
	void toObjectMapShouldHandleDuplicateValues() {
		Map<String, Value> payload = Map.of("field1", Value.newBuilder().setStringValue("same").build(), "field2",
				Value.newBuilder().setStringValue("same").build(), "field3",
				Value.newBuilder().setIntegerValue(1).build(), "field4", Value.newBuilder().setIntegerValue(1).build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payload);

		assertThat(result).hasSize(4);
		assertThat(result.get("field1")).isEqualTo("same");
		assertThat(result.get("field2")).isEqualTo("same");
		assertThat(result.get("field3")).isEqualTo(1L);
		assertThat(result.get("field4")).isEqualTo(1L);
	}

}
