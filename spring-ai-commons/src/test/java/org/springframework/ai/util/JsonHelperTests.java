/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.util;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link JsonHelper} class.
 *
 * @author Sebastien Deleuze
 * @author Oleksandr Klymenko
 */
class JsonHelperTests {

	private final JsonHelper jsonHelper = new JsonHelper();

	@Test
	void shouldThrowExceptionWhenJsonIsNull() {
		assertThatThrownBy(() -> this.jsonHelper.fromJson(null, TestRecord.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("json cannot be null");
	}

	@Test
	void shouldReturnNullWhenJsonIsJsonNull() {
		assertThat(this.jsonHelper.fromJson("null", TestRecord.class)).isNull();
	}

	@Test
	void shouldThrowExceptionWhenClassIsNull() {
		assertThatThrownBy(() -> this.jsonHelper.fromJson("{}", (Class<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenTypeIsNull() {
		assertThatThrownBy(() -> this.jsonHelper.fromJson("{}", (ParameterizedTypeReference<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void fromJsonToObject() {
		var json = """
					{
						"name" : "John",
						"age" : 30
					}
				""";
		var object = this.jsonHelper.fromJson(json, TestRecord.class);
		Assertions.assertThat(object).isNotNull();
		Assertions.assertThat(object.name).isEqualTo("John");
		Assertions.assertThat(object.age).isEqualTo(30);
	}

	@Test
	void fromJsonToObjectWithMissingProperty() {
		var json = """
					{
						"name": "John"
					}
				""";
		var object = this.jsonHelper.fromJson(json, TestRecord.class);
		Assertions.assertThat(object).isNotNull();
		Assertions.assertThat(object.name).isEqualTo("John");
		Assertions.assertThat(object.age).isNull();
	}

	@Test
	void fromJsonToObjectWithNullProperty() {
		var json = """
					{
						"name": "John",
						"age": null
					}
				""";
		var object = this.jsonHelper.fromJson(json, TestRecord.class);
		Assertions.assertThat(object).isNotNull();
		Assertions.assertThat(object.name).isEqualTo("John");
		Assertions.assertThat(object.age).isNull();
	}

	@Test
	void fromJsonToObjectWithOtherNullProperty() {
		var json = """
					{
						"name": null,
						"age": 21
					}
				""";
		var object = this.jsonHelper.fromJson(json, TestRecord.class);
		Assertions.assertThat(object).isNotNull();
		Assertions.assertThat(object.name).isNull();
		Assertions.assertThat(object.age).isEqualTo(21);
	}

	@Test
	void fromJsonToObjectWithUnknownProperty() {
		var json = """
					{
						"name": "James",
						"surname": "Bond"
					}
				""";
		var object = this.jsonHelper.fromJson(json, TestRecord.class);
		Assertions.assertThat(object).isNotNull();
		Assertions.assertThat(object.name).isEqualTo("James");
	}

	@Test
	void fromJsonToObjectWithType() {
		var json = """
					{
						"name" : "John",
						"age" : 30
					}
				""";
		TestRecord object = this.jsonHelper.fromJson(json, (Type) TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isEqualTo("John");
		assertThat(object.age).isEqualTo(30);
	}

	@Test
	void fromObjectToJson() {
		var object = new TestRecord("John", 30);
		var json = this.jsonHelper.toJson(object);
		Assertions.assertThat(json).isEqualToIgnoringWhitespace("""
					{
						"name" : "John",
						"age" : 30
					}
				""");
	}

	@Test
	void fromObjectToJsonWithNullValues() {
		var object = new TestRecord("John", null);
		var json = this.jsonHelper.toJson(object);
		Assertions.assertThat(json).isEqualToIgnoringWhitespace("""
					{
						"name" : "John",
						"age" : null
					}
				""");
	}

	@Test
	void fromNullObjectToJson() {
		var json = this.jsonHelper.toJson(null);
		Assertions.assertThat(json).isEqualToIgnoringWhitespace("null");
	}

	@Test
	void fromObjectToString() {
		var value = this.jsonHelper.convertToTypedObject("John", String.class);
		Assertions.assertThat(value).isOfAnyClassIn(String.class);
		Assertions.assertThat(value).isEqualTo("John");
	}

	@Test
	void fromObjectToByte() {
		var value = this.jsonHelper.convertToTypedObject("1", Byte.class);
		Assertions.assertThat(value).isOfAnyClassIn(Byte.class);
		Assertions.assertThat(value).isEqualTo((byte) 1);
	}

	@Test
	void fromObjectToInteger() {
		var value = this.jsonHelper.convertToTypedObject("1", Integer.class);
		Assertions.assertThat(value).isOfAnyClassIn(Integer.class);
		Assertions.assertThat(value).isEqualTo(1);
	}

	@Test
	void fromObjectToShort() {
		var value = this.jsonHelper.convertToTypedObject("1", Short.class);
		Assertions.assertThat(value).isOfAnyClassIn(Short.class);
		Assertions.assertThat(value).isEqualTo((short) 1);
	}

	@Test
	void fromObjectToLong() {
		var value = this.jsonHelper.convertToTypedObject("1", Long.class);
		Assertions.assertThat(value).isOfAnyClassIn(Long.class);
		Assertions.assertThat(value).isEqualTo(1L);
	}

	@Test
	void fromObjectToDouble() {
		var value = this.jsonHelper.convertToTypedObject("1.0", Double.class);
		Assertions.assertThat(value).isOfAnyClassIn(Double.class);
		Assertions.assertThat(value).isEqualTo(1.0);
	}

	@Test
	void fromObjectToFloat() {
		var value = this.jsonHelper.convertToTypedObject("1.0", Float.class);
		Assertions.assertThat(value).isOfAnyClassIn(Float.class);
		Assertions.assertThat(value).isEqualTo(1.0f);
	}

	@Test
	void fromObjectToBoolean() {
		var value = this.jsonHelper.convertToTypedObject("true", Boolean.class);
		Assertions.assertThat(value).isOfAnyClassIn(Boolean.class);
		Assertions.assertThat(value).isEqualTo(true);
	}

	@Test
	void fromObjectToEnum() {
		var value = this.jsonHelper.convertToTypedObject("VALUE", TestEnum.class);
		Assertions.assertThat(value).isOfAnyClassIn(TestEnum.class);
		Assertions.assertThat(value).isEqualTo(TestEnum.VALUE);
	}

	@Test
	void fromObjectToRecord() {
		var record = new TestRecord("John", 30);
		var value = this.jsonHelper.convertToTypedObject(record, TestRecord.class);
		Assertions.assertThat(value).isOfAnyClassIn(TestRecord.class);
		Assertions.assertThat(value).isEqualTo(new TestRecord("John", 30));
	}

	@Test
	void fromStringToObject() {
		String jsonString = """
				{
					"name": "foo",
					"age": 7
				}
				""";
		var value = this.jsonHelper.convertToTypedObject(jsonString, TestSimpleObject.class);
		Assertions.assertThat(value).isOfAnyClassIn(TestSimpleObject.class);

		TestSimpleObject testSimpleObject = (TestSimpleObject) value;
		assertThat(testSimpleObject.name).isEqualTo("foo");
		assertThat(testSimpleObject.age).isEqualTo(7);
	}

	@Test
	void fromScientificNotationToInteger() {
		var value = this.jsonHelper.convertToTypedObject("1.5E7", Integer.class);
		Assertions.assertThat(value).isInstanceOf(Integer.class);
		Assertions.assertThat(value).isEqualTo(15_000_000);
	}

	@Test
	void fromScientificNotationToLong() {
		var value = this.jsonHelper.convertToTypedObject("1.5E12", Long.class);
		Assertions.assertThat(value).isInstanceOf(Long.class);
		Assertions.assertThat(value).isEqualTo(1_500_000_000_000L);
	}

	@Test
	void localDateTime() {
		String input = "2026-04-19T07:12:00";
		LocalDateTime result = (LocalDateTime) this.jsonHelper.convertToTypedObject(input, LocalDateTime.class);
		assertThat(result.getYear()).isEqualTo(2026);
	}

	@Test
	void shouldThrowExceptionWhenJsonMapperIsNull() {
		assertThatThrownBy(() -> new JsonHelper(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("jsonMapper cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenJsonIsMalformed() {
		var json = "{ value }";
		assertThatThrownBy(() -> this.jsonHelper.fromJson(json, TestRecord.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Conversion from JSON to")
			.hasMessageContaining("failed");
	}

	@Test
	void shouldThrowExceptionWhenJsonIsMalformedWithType() {
		var json = "{ value: }";
		assertThatThrownBy(() -> this.jsonHelper.fromJson(json, (Type) TestRecord.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Conversion from JSON to")
			.hasMessageContaining("failed");
	}

	@Test
	void shouldThrowExceptionWhenJsonIsMalformedWithParameterizedTypeReference() {
		var json = "[ 1, 2, }";
		assertThatThrownBy(() -> this.jsonHelper.fromJson(json, new ParameterizedTypeReference<List<Integer>>() {
		})).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Conversion from JSON to")
			.hasMessageContaining("failed");
	}

	@Test
	void fromJsonToListWithParameterizedTypeReference() {
		var json = "[1, 2, 3]";
		var list = this.jsonHelper.fromJson(json, new ParameterizedTypeReference<List<Integer>>() {
		});
		assertThat(list).containsExactly(1, 2, 3);
	}

	@Test
	void fromJsonToMapWithParameterizedTypeReference() {
		var json = """
					{
						"stringValue": "string",
						"intValue": "1"
					}
				""";
		var map = this.jsonHelper.fromJson(json, new ParameterizedTypeReference<Map<String, String>>() {
		});
		assertThat(map).containsEntry("stringValue", "string").containsEntry("intValue", "1");
	}

	@Test
	void fromJsonToMap() {
		var json = """
					{
						"name": "John",
						"age": 30
					}
				""";
		assertThat(this.jsonHelper.fromJsonToMap(json)).containsEntry("name", "John").containsEntry("age", 30);
	}

	@Test
	void fromEmptyJsonObject() {
		var object = this.jsonHelper.fromJson("{}", TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isNull();
		assertThat(object.age).isNull();
	}

	@Test
	void fromEmptyJsonArray() {
		var list = this.jsonHelper.fromJson("[]", new ParameterizedTypeReference<List<String>>() {
		});
		assertThat(list).isEmpty();
	}

	@Test
	void doesNotDoubleSerializeValidJsonStringWhenForwarding() {
		var input = "[1,2,3]";
		assertThat(this.jsonHelper.toJson(input, true)).isEqualTo(input);
	}

	@Test
	void serializesValidJsonStringWhenNotForwarding() {
		assertThat(this.jsonHelper.toJson("[1,2,3]")).isEqualTo("\"[1,2,3]\"");
	}

	@Test
	void serializesNonJsonStringWhenForwarding() {
		assertThat(this.jsonHelper.toJson("not json at all", true)).isEqualTo("\"not json at all\"");
	}

	@Test
	void shouldThrowExceptionWhenValueIsNullInConvertToTypedObject() {
		assertThatThrownBy(() -> this.jsonHelper.convertToTypedObject(null, String.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenTypeIsNullInConvertToTypedObject() {
		assertThatThrownBy(() -> this.jsonHelper.convertToTypedObject("test", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void fromObjectToPrimitiveInt() {
		var value = this.jsonHelper.convertToTypedObject("1", int.class);
		assertThat(value).isInstanceOf(Integer.class);
		assertThat(value).isEqualTo(1);
	}

	@Test
	void fromObjectToPrimitiveLong() {
		var value = this.jsonHelper.convertToTypedObject("1", long.class);
		assertThat(value).isInstanceOf(Long.class);
		assertThat(value).isEqualTo(1L);
	}

	@Test
	void fromObjectToPrimitiveBoolean() {
		var value = this.jsonHelper.convertToTypedObject("true", boolean.class);
		assertThat(value).isInstanceOf(Boolean.class);
		assertThat(value).isEqualTo(true);
	}

	@Test
	void shouldThrowExceptionWhenConversionFails() {
		assertThatThrownBy(() -> this.jsonHelper.convertToTypedObject("not_a_number", Integer.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("java.lang.Integer");
	}

	@Test
	void convertToTypedObjectWithNonParsableJsonString() {
		assertThatThrownBy(() -> this.jsonHelper.convertToTypedObject("not json at all", TestRecord.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Conversion from JSON to");
	}

	record TestRecord(String name, Integer age) {
	}

	static class TestSimpleObject {

		public String name;

		public int age;

	}

	enum TestEnum {

		VALUE

	}

}
