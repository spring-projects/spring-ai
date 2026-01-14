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

package org.springframework.ai.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class ModelOptionsUtilsTests {

	@Test
	public void merge() {
		TestPortableOptionsImpl portableOptions = new TestPortableOptionsImpl();
		portableOptions.setName("John");
		portableOptions.setAge(30);
		portableOptions.setNonInterfaceField("NonInterfaceField");

		TestSpecificOptions specificOptions = new TestSpecificOptions();
		specificOptions.setName("Mike");
		specificOptions.setSpecificField("SpecificField");

		assertThatThrownBy(
				() -> ModelOptionsUtils.merge(portableOptions, specificOptions, TestPortableOptionsImpl.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No @JsonProperty fields found in the ");

		var specificOptions2 = ModelOptionsUtils.merge(portableOptions, specificOptions, TestSpecificOptions.class);

		assertThat(specificOptions2.getAge()).isEqualTo(30);
		assertThat(specificOptions2.getName()).isEqualTo("John"); // !!! Overridden by the
		// portableOptions
		assertThat(specificOptions2.getSpecificField()).isEqualTo("SpecificField");
	}

	@Test
	public void objectToMap() {
		TestPortableOptionsImpl portableOptions = new TestPortableOptionsImpl();
		portableOptions.setName("John");
		portableOptions.setAge(30);
		portableOptions.setNonInterfaceField("NonInterfaceField");

		Map<String, Object> map = ModelOptionsUtils.objectToMap(portableOptions);

		assertThat(map).containsEntry("name", "John");
		assertThat(map).containsEntry("age", 30);
		assertThat(map).containsEntry("nonInterfaceField", "NonInterfaceField");
	}

	@Test
	public void mapToClass() {
		TestPortableOptionsImpl portableOptions = ModelOptionsUtils.mapToClass(
				Map.of("name", "John", "age", 30, "nonInterfaceField", "NonInterfaceField"),
				TestPortableOptionsImpl.class);

		assertThat(portableOptions.getName()).isEqualTo("John");
		assertThat(portableOptions.getAge()).isEqualTo(30);
		assertThat(portableOptions.getNonInterfaceField()).isEqualTo("NonInterfaceField");
	}

	@Test
	public void mergeBeans() {

		var portableOptions = new TestPortableOptionsImpl();
		portableOptions.setName("John");
		portableOptions.setAge(30);
		portableOptions.setNonInterfaceField("NonInterfaceField");

		var specificOptions = new TestSpecificOptions();

		specificOptions.setName("Mike");
		specificOptions.setAge(60);
		specificOptions.setSpecificField("SpecificField");

		TestSpecificOptions specificOptions2 = ModelOptionsUtils.mergeBeans(portableOptions, specificOptions,
				TestPortableOptions.class, false);

		assertThat(specificOptions2.getAge()).isEqualTo(60);
		assertThat(specificOptions2.getName()).isEqualTo("Mike");
		assertThat(specificOptions2.getSpecificField()).isEqualTo("SpecificField");

		TestSpecificOptions specificOptionsWithOverride = ModelOptionsUtils.mergeBeans(portableOptions, specificOptions,
				TestPortableOptions.class, true);

		assertThat(specificOptionsWithOverride.getAge()).isEqualTo(30);
		assertThat(specificOptionsWithOverride.getName()).isEqualTo("John");
		assertThat(specificOptionsWithOverride.getSpecificField()).isEqualTo("SpecificField");
	}

	@Test
	public void copyToTarget() {
		var portableOptions = new TestPortableOptionsImpl();
		portableOptions.setName("John");
		portableOptions.setAge(30);
		portableOptions.setNonInterfaceField("NonInterfaceField");

		TestSpecificOptions target = ModelOptionsUtils.copyToTarget(portableOptions, TestPortableOptions.class,
				TestSpecificOptions.class);

		assertThat(target.getAge()).isEqualTo(30);
		assertThat(target.getName()).isEqualTo("John");
		assertThat(target.getSpecificField()).isNull();
	}

	@Test
	public void jsonToMap_emptyStringAsNullObject() {
		String json = "{\"name\":\"\", \"age\":30}";
		// For Map: empty string remains ""
		Map<String, Object> map = ModelOptionsUtils.jsonToMap(json);
		assertThat(map.get("name")).isEqualTo("");
		assertThat(map.get("age")).isEqualTo(30);

		// Custom JsonMapper: still "" for Map
		JsonMapper strictMapper = JsonMapper.builder()
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.build();
		Map<String, Object> mapStrict = ModelOptionsUtils.jsonToMap(json, strictMapper);
		assertThat(mapStrict.get("name")).isEqualTo("");
	}

	@Test
	public void pojo_emptyStringAsNullObject() throws Exception {
		String json = "{\"name\":\"\", \"age\":30}";

		// POJO with default OBJECT_MAPPER (feature enabled)
		Person person = ModelOptionsUtils.JSON_MAPPER.readValue(json, Person.class);
		assertThat(person.name).isEqualTo(""); // String remains ""
		assertThat(person.age).isEqualTo(30); // Integer is fine

		String jsonWithEmptyAge = "{\"name\":\"John\", \"age\":\"\"}";
		Person person2 = ModelOptionsUtils.JSON_MAPPER.readValue(jsonWithEmptyAge, Person.class);
		assertThat(person2.name).isEqualTo("John");
		assertThat(person2.age).isNull(); // Integer: "" → null

		// TODO: Need to investigate why the below fails
		// // POJO with feature disabled: should fail for Integer field
		// JsonMapper strictMapper = JsonMapper.builder()
		// .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
		// .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
		// .build();
		// assertThatThrownBy(() -> strictMapper.readValue(jsonWithEmptyAge,
		// Person.class)).isInstanceOf(Exception.class);
	}

	@Test
	public void getJsonPropertyValues() {
		record TestRecord(@JsonProperty("field1") String fieldA, @JsonProperty("field2") String fieldB) {

		}
		assertThat(ModelOptionsUtils.getJsonPropertyValues(TestRecord.class)).hasSize(2);
		assertThat(ModelOptionsUtils.getJsonPropertyValues(TestRecord.class)).containsExactly("field1", "field2");
	}

	@Test
	public void enumCoercion_emptyStringAsNull() {
		// Test direct enum deserialization with empty string
		ColorEnum colorEnum = ModelOptionsUtils.JSON_MAPPER.readValue("\"\"", ColorEnum.class);
		assertThat(colorEnum).isNull();

		// Test direct enum deserialization with valid value
		colorEnum = ModelOptionsUtils.JSON_MAPPER.readValue("\"RED\"", ColorEnum.class);
		assertThat(colorEnum).isEqualTo(ColorEnum.RED);

		// Test direct enum deserialization with invalid value should throw exception
		final String jsonInvalid = "\"Invalid\"";
		assertThatThrownBy(() -> ModelOptionsUtils.JSON_MAPPER.readValue(jsonInvalid, ColorEnum.class))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	public void enumCoercion_jsonMapperConfiguration() {
		// Test that ModelOptionsUtils.JSON_MAPPER has the correct coercion
		// configuration
		// This validates that our static configuration block is working

		// Empty string should coerce to null for enums
		ColorEnum colorEnum = ModelOptionsUtils.JSON_MAPPER.readValue("\"\"", ColorEnum.class);
		assertThat(colorEnum).isNull();

		// Null should remain null
		colorEnum = ModelOptionsUtils.JSON_MAPPER.readValue("null", ColorEnum.class);
		assertThat(colorEnum).isNull();

		// Valid enum values should deserialize correctly
		colorEnum = ModelOptionsUtils.JSON_MAPPER.readValue("\"BLUE\"", ColorEnum.class);
		assertThat(colorEnum).isEqualTo(ColorEnum.BLUE);
	}

	@Test
	public void enumCoercion_apiResponseWithFinishReason() {
		// Test case 1: Empty string finish_reason should deserialize to null
		String jsonWithEmptyFinishReason = """
				{
					"id": "test-123",
					"finish_reason": ""
				}
				""";

		TestApiResponse response = ModelOptionsUtils.JSON_MAPPER.readValue(jsonWithEmptyFinishReason,
				TestApiResponse.class);
		assertThat(response.id()).isEqualTo("test-123");
		assertThat(response.finishReason()).isNull();

		// Test case 2: Valid finish_reason should deserialize correctly (using JSON
		// property value)
		String jsonWithValidFinishReason = """
				{
					"id": "test-456",
					"finish_reason": "stop"
				}
				""";

		response = ModelOptionsUtils.JSON_MAPPER.readValue(jsonWithValidFinishReason, TestApiResponse.class);
		assertThat(response.id()).isEqualTo("test-456");
		assertThat(response.finishReason()).isEqualTo(TestFinishReason.STOP);

		// Test case 3: Null finish_reason should remain null
		String jsonWithNullFinishReason = """
				{
					"id": "test-789",
					"finish_reason": null
				}
				""";

		response = ModelOptionsUtils.JSON_MAPPER.readValue(jsonWithNullFinishReason, TestApiResponse.class);
		assertThat(response.id()).isEqualTo("test-789");
		assertThat(response.finishReason()).isNull();

		// Test case 4: Invalid finish_reason should throw exception
		String jsonWithInvalidFinishReason = """
				{
					"id": "test-error",
					"finish_reason": "INVALID_VALUE"
				}
				""";

		assertThatThrownBy(
				() -> ModelOptionsUtils.JSON_MAPPER.readValue(jsonWithInvalidFinishReason, TestApiResponse.class))
			.hasMessageContaining("INVALID_VALUE");
	}

	public enum ColorEnum {

		RED, GREEN, BLUE

	}

	public enum TestFinishReason {

		@JsonProperty("stop")
		STOP, @JsonProperty("length")
		LENGTH, @JsonProperty("content_filter")
		CONTENT_FILTER

	}

	public record TestApiResponse(@JsonProperty("id") String id,
			@JsonProperty("finish_reason") TestFinishReason finishReason) {
	}

	public static class Person {

		public String name;

		public Integer age;

	}

	public interface TestPortableOptions extends ModelOptions {

		String getName();

		void setName(String name);

		Integer getAge();

		void setAge(Integer age);

	}

	public static class TestPortableOptionsImpl implements TestPortableOptions {

		private String name;

		private Integer age;

		// Non interface fields
		private String nonInterfaceField;

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public void setName(String name) {
			this.name = name;
		}

		@Override
		public Integer getAge() {
			return this.age;
		}

		@Override
		public void setAge(Integer age) {
			this.age = age;
		}

		public String getNonInterfaceField() {
			return this.nonInterfaceField;
		}

		public void setNonInterfaceField(String nonInterfaceField) {
			this.nonInterfaceField = nonInterfaceField;
		}

	}

	public static class TestSpecificOptions implements TestPortableOptions {

		@JsonProperty("specificField")
		private String specificField;

		@JsonProperty("name")
		private String name;

		@JsonProperty("age")
		private Integer age;

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public void setName(String name) {
			this.name = name;
		}

		@Override
		public Integer getAge() {
			return this.age;
		}

		@Override
		public void setAge(Integer age) {
			this.age = age;
		}

		public String getSpecificField() {
			return this.specificField;
		}

		public void setSpecificField(String modelSpecificField) {
			this.specificField = modelSpecificField;
		}

		@Override
		public String toString() {
			return "TestModelSpecificOptions{" + "specificField='" + this.specificField + '\'' + ", name='" + this.name
					+ '\'' + ", age=" + this.age + '}';
		}

	}

}
