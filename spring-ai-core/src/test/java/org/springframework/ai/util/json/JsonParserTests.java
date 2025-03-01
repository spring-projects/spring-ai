package org.springframework.ai.util.json;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link JsonParser} class.
 *
 * @author Thomas Vitale
 */
class JsonParserTests {

	@Test
	void shouldGetObjectMapper() {
		var objectMapper = JsonParser.getObjectMapper();
		assertThat(objectMapper).isNotNull();
	}

	@Test
	void shouldThrowExceptionWhenJsonIsNull() {
		assertThatThrownBy(() -> JsonParser.fromJson(null, TestRecord.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("json cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenClassIsNull() {
		assertThatThrownBy(() -> JsonParser.fromJson("{}", (Class<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenTypeIsNull() {
		assertThatThrownBy(() -> JsonParser.fromJson("{}", (TypeReference<?>) null))
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
		var object = JsonParser.fromJson(json, TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isEqualTo("John");
		assertThat(object.age).isEqualTo(30);
	}

	@Test
	void fromJsonToObjectWithMissingProperty() {
		var json = """
				    {
				        "name": "John"
				    }
				""";
		var object = JsonParser.fromJson(json, TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isEqualTo("John");
		assertThat(object.age).isNull();
	}

	@Test
	void fromJsonToObjectWithNullProperty() {
		var json = """
				    {
				        "name": "John",
				        "age": null
				    }
				""";
		var object = JsonParser.fromJson(json, TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isEqualTo("John");
		assertThat(object.age).isNull();
	}

	@Test
	void fromJsonToObjectWithOtherNullProperty() {
		var json = """
				    {
				        "name": null,
				        "age": 21
				    }
				""";
		var object = JsonParser.fromJson(json, TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isNull();
		assertThat(object.age).isEqualTo(21);
	}

	@Test
	void fromJsonToObjectWithUnknownProperty() {
		var json = """
				    {
				        "name": "James",
				        "surname": "Bond"
				    }
				""";
		var object = JsonParser.fromJson(json, TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isEqualTo("James");
	}

	@Test
	void fromJsonToObjectWithType() {
		var json = """
				    {
				      "name" : "John",
				      "age" : 30
				    }
				""";
		TestRecord object = JsonParser.fromJson(json, (Type) TestRecord.class);
		assertThat(object).isNotNull();
		assertThat(object.name).isEqualTo("John");
		assertThat(object.age).isEqualTo(30);
	}

	@Test
	void fromObjectToJson() {
		var object = new TestRecord("John", 30);
		var json = JsonParser.toJson(object);
		assertThat(json).isEqualToIgnoringWhitespace("""
				    {
				      "name" : "John",
				      "age" : 30
				    }
				""");
	}

	@Test
	void fromObjectToJsonWithNullValues() {
		var object = new TestRecord("John", null);
		var json = JsonParser.toJson(object);
		assertThat(json).isEqualToIgnoringWhitespace("""
				    {
				      "name" : "John",
				      "age" : null
				    }
				""");
	}

	@Test
	void fromNullObjectToJson() {
		var json = JsonParser.toJson(null);
		assertThat(json).isEqualToIgnoringWhitespace("null");
	}

	@Test
	void fromObjectToString() {
		var value = JsonParser.toTypedObject("John", String.class);
		assertThat(value).isOfAnyClassIn(String.class);
		assertThat(value).isEqualTo("John");
	}

	@Test
	void fromObjectToByte() {
		var value = JsonParser.toTypedObject("1", Byte.class);
		assertThat(value).isOfAnyClassIn(Byte.class);
		assertThat(value).isEqualTo((byte) 1);
	}

	@Test
	void fromObjectToInteger() {
		var value = JsonParser.toTypedObject("1", Integer.class);
		assertThat(value).isOfAnyClassIn(Integer.class);
		assertThat(value).isEqualTo(1);
	}

	@Test
	void fromObjectToShort() {
		var value = JsonParser.toTypedObject("1", Short.class);
		assertThat(value).isOfAnyClassIn(Short.class);
		assertThat(value).isEqualTo((short) 1);
	}

	@Test
	void fromObjectToLong() {
		var value = JsonParser.toTypedObject("1", Long.class);
		assertThat(value).isOfAnyClassIn(Long.class);
		assertThat(value).isEqualTo(1L);
	}

	@Test
	void fromObjectToDouble() {
		var value = JsonParser.toTypedObject("1.0", Double.class);
		assertThat(value).isOfAnyClassIn(Double.class);
		assertThat(value).isEqualTo(1.0);
	}

	@Test
	void fromObjectToFloat() {
		var value = JsonParser.toTypedObject("1.0", Float.class);
		assertThat(value).isOfAnyClassIn(Float.class);
		assertThat(value).isEqualTo(1.0f);
	}

	@Test
	void fromObjectToBoolean() {
		var value = JsonParser.toTypedObject("true", Boolean.class);
		assertThat(value).isOfAnyClassIn(Boolean.class);
		assertThat(value).isEqualTo(true);
	}

	@Test
	void fromObjectToEnum() {
		var value = JsonParser.toTypedObject("VALUE", TestEnum.class);
		assertThat(value).isOfAnyClassIn(TestEnum.class);
		assertThat(value).isEqualTo(TestEnum.VALUE);
	}

	@Test
	void fromObjectToRecord() {
		var record = new TestRecord("John", 30);
		var value = JsonParser.toTypedObject(record, TestRecord.class);
		assertThat(value).isOfAnyClassIn(TestRecord.class);
		assertThat(value).isEqualTo(new TestRecord("John", 30));
	}

	record TestRecord(String name, Integer age) {
	}

	enum TestEnum {

		VALUE

	}

}
