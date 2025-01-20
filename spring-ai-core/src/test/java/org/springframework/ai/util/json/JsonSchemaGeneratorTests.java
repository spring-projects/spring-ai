package org.springframework.ai.util.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonSchemaGenerator}.
 *
 * @author Thomas Vitale
 */
class JsonSchemaGeneratorTests {

	@Test
	void generateSchemaForMethodWithSimpleParameters() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("simpleMethod", String.class, int.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "name": {
				            "type": "string"
				        },
				        "age": {
				            "type": "integer",
				            "format" : "int32"
				        }
				    },
				    "required": [
				        "name",
				        "age"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithJsonPropertyAnnotations() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("annotatedMethod", String.class, String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method,
				JsonSchemaGenerator.SchemaOption.RESPECT_JSON_PROPERTY_REQUIRED);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "username": {
				            "type": "string"
				        },
				        "password": {
				            "type": "string"
				        }
				    },
				    "required": [
				        "password"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithAdditionalPropertiesAllowed() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("simpleMethod", String.class, int.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method,
				JsonSchemaGenerator.SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT);

		JsonNode jsonNode = JsonParser.getObjectMapper().readTree(schema);
		assertThat(jsonNode.has("additionalProperties")).isFalse();
	}

	@Test
	void generateSchemaForMethodWithUpperCaseTypes() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("simpleMethod", String.class, int.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method,
				JsonSchemaGenerator.SchemaOption.UPPER_CASE_TYPE_VALUES);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "OBJECT",
				    "properties": {
				        "name": {
				            "type": "STRING"
				        },
				        "age": {
				            "type": "INTEGER",
				            "format" : "int32"
				        }
				    },
				    "required": [
				        "name",
				        "age"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithComplexParameters() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("complexMethod", List.class, TestData.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);

		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "items": {
				            "type": "array",
				            "items": {
				                "type": "string"
				            }
				        },
				        "data": {
				            "type": "object",
				            "properties": {
				                "id": {
				                    "type": "integer",
				                    "format" : "int32"
				                },
				                "name": {
				                    "type": "string"
				                }
				            }
				        }
				    },
				    "required": [
				        "items",
				        "data"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithTimeParameters() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("timeMethod", Duration.class, LocalDateTime.class,
				Instant.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "duration": {
				            "type": "string",
				            "format" : "duration"
				        },
				        "localDateTime": {
				            "type": "string",
				            "format": "date-time"
				        },
				        "instant": {
				            "type": "string",
				            "format": "date-time"
				        }
				    },
				    "required": [
				        "duration",
				        "localDateTime",
				        "instant"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForSimpleType() {
		String schema = JsonSchemaGenerator.generateForType(Person.class);
		String expectedJsonSchema = """
				{
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string"
				        },
				        "id": {
				            "type": "integer",
				            "format" : "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithAdditionalPropertiesAllowed() throws JsonProcessingException {
		String schema = JsonSchemaGenerator.generateForType(Person.class,
				JsonSchemaGenerator.SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT);

		JsonNode jsonNode = JsonParser.getObjectMapper().readTree(schema);
		assertThat(jsonNode.has("additionalProperties")).isFalse();
	}

	@Test
	void generateSchemaForTypeWithUpperCaseValues() {
		String schema = JsonSchemaGenerator.generateForType(Person.class,
				JsonSchemaGenerator.SchemaOption.UPPER_CASE_TYPE_VALUES);
		String expectedJsonSchema = """
				{
				    "type": "OBJECT",
				    "properties": {
				        "email": {
				            "type": "STRING"
				        },
				        "id": {
				            "type": "INTEGER",
				            "format" : "int32"
				        },
				        "name": {
				            "type": "STRING"
				        }
				    },
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForRecord() {
		String schema = JsonSchemaGenerator.generateForType(TestData.class);
		String expectedJsonSchema = """
				{
				    "type": "object",
				    "properties": {
				        "id": {
				            "type": "integer",
				            "format" : "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForEnum() {
		String schema = JsonSchemaGenerator.generateForType(Month.class);
		String expectedJsonSchema = """
				{
				    "type": "string",
				    "enum": [
				        "JANUARY",
				        "FEBRUARY",
				        "MARCH",
				        "APRIL",
				        "MAY",
				        "JUNE",
				        "JULY",
				        "AUGUST",
				        "SEPTEMBER",
				        "OCTOBER",
				        "NOVEMBER",
				        "DECEMBER"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void throwExceptionWhenTypeIsNull() {
		assertThatThrownBy(() -> JsonSchemaGenerator.generateForType(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	static class TestMethods {

		public void simpleMethod(String name, int age) {
		}

		public void annotatedMethod(String username, @JsonProperty(required = true) String password) {
		}

		public void complexMethod(List<String> items, TestData data) {
		}

		public void timeMethod(Duration duration, LocalDateTime localDateTime, Instant instant) {
		}

	}

	record TestData(int id, String name) {
	}

	static class Person {

		private int id;

		private String name;

		private String email;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

	}

}
