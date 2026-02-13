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

package org.springframework.ai.util.json;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonSchemaGenerator}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class JsonSchemaGeneratorTests {

	// METHODS

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
				            "type": "integer"
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
	void generateSchemaForMethodWithToolParamAnnotations() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("annotatedMethod", String.class, String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "username": {
				            "type": "string",
				            "description": "The username of the customer"
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
	void generateSchemaForMethodWhenParameterRequiredByDefault() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("anotherAnnotatedMethod", String.class, String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
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
				    	"username",
				        "password"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithOpenApiSchemaAnnotations() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("openApiMethod", String.class, String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "username": {
				            "type": "string",
				            "description": "The username of the customer"
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
	void generateSchemaForMethodWithObjectParam() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("objectParamMethod", Object.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "object": {
				        }
				    },
				    "required": [
				        "object"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithJacksonAnnotations() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("jacksonMethod", String.class, String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "username": {
				            "type": "string",
				            "description": "The username of the customer"
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
	void generateSchemaForMethodWithNullableAnnotations() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("nullableMethod", String.class, String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
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

		JsonNode jsonNode = JsonParser.getJsonMapper().readTree(schema);
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
				            "type": "INTEGER"
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
		Method method = TestMethods.class.getDeclaredMethod("complexMethod", List.class, TestData.class,
				MoreTestData.class);

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
				                    "format": "int32"
				                },
				                "name": {
				                    "type": "string",
				                    "description": "The special name"
				                }
				            },
				            "required": [ "id", "name" ]
				        },
				        "moreData": {
				            "type": "object",
				            "properties": {
				                "id": {
				                	"type": "integer",
				                    "format": "int32"
				                },
				                "name": {
									"type": "string",
									"description": "Even more special name"
							  	}
				            },
				            "required": [ "id", "name" ],
				            "description" : "Much more data"
				        }
				    },
				    "required": [ "items", "data", "moreData" ],
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
				            "type": "string"
				        },
				        "localDateTime": {
				            "type": "string"
				        },
				        "instant": {
				            "type": "string"
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
	void generateSchemaForMethodWithToolContext() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("contextMethod", String.class, LocalDateTime.class,
				ToolContext.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		String expectedJsonSchema = """
				{
				    "$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "deliveryStatus": {
				            "type": "string"
				        },
				        "expectedDelivery": {
				            "type": "string"
				        }
				    },
				    "required": [
				        "deliveryStatus",
				        "expectedDelivery"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	// TYPES

	@Test
	void generateSchemaForSimpleType() {
		String schema = JsonSchemaGenerator.generateForType(Person.class);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string"
				        },
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "required": [ "email", "id", "name" ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithAdditionalPropertiesAllowed() {
		String schema = JsonSchemaGenerator.generateForType(Person.class,
				JsonSchemaGenerator.SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT);

		JsonNode jsonNode = JsonParser.getJsonMapper().readTree(schema);
		assertThat(jsonNode.has("additionalProperties")).isFalse();
	}

	@Test
	void generateSchemaWhenParameterRequiredByDefault() {
		String schema = JsonSchemaGenerator.generateForType(Person.class);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string"
				        },
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "required": [
				    	"email",
				        "id",
				        "name"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithToolArgAnnotation() {
		String schema = JsonSchemaGenerator.generateForType(AnnotatedPerson.class);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string",
				            "description": "The email of the person"
				        },
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "required": [
				        "id",
				        "name"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithOpenApiAnnotation() {
		String schema = JsonSchemaGenerator.generateForType(OpenApiPerson.class);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string",
				            "description": "The email of the person"
				        },
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "required": [
				        "id",
				        "name"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithJacksonAnnotation() {
		String schema = JsonSchemaGenerator.generateForType(JacksonPerson.class);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string"
				        },
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "required": [
				        "id",
				        "name"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithNullableAnnotation() {
		String schema = JsonSchemaGenerator.generateForType(JacksonPerson.class);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "email": {
				            "type": "string"
				        },
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string"
				        }
				    },
				    "required": [
				        "id",
				        "name"
				    ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForTypeWithUpperCaseValues() {
		String schema = JsonSchemaGenerator.generateForType(Person.class,
				JsonSchemaGenerator.SchemaOption.UPPER_CASE_TYPE_VALUES);
		String expectedJsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "OBJECT",
				    "properties": {
				        "email": {
				            "type": "STRING"
				        },
				        "id": {
				            "type": "INTEGER",
				            "format": "int32"
				        },
				        "name": {
				            "type": "STRING"
				        }
				    },
				    "required": [ "email", "id", "name" ],
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
					"$schema": "https://json-schema.org/draft/2020-12/schema",
				    "type": "object",
				    "properties": {
				        "id": {
				            "type": "integer",
				            "format": "int32"
				        },
				        "name": {
				            "type": "string",
				            "description": "The special name"
				        }
				    },
				    "required": [ "id", "name" ],
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
					"$schema": "https://json-schema.org/draft/2020-12/schema",
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
	void generateSchemaForTypeWithJSpecifyNullableField() {
		String schema = JsonSchemaGenerator.generateForType(JSpecifyNullablePerson.class);
		String expectedJsonSchema = """
						{
						  "$schema" : "https://json-schema.org/draft/2020-12/schema",
						  "type" : "object",
						  "properties" : {
						    "email" : {
						      "type" : "string"
						    },
						    "id" : {
						      "type" : "integer",
						      "format" : "int32"
						    },
						    "name" : {
						      "type" : "string"
						    }
						  },
						  "required" : [ "id", "name" ],
						  "additionalProperties" : false
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

		public void objectParamMethod(Object object) {
		}

		public void annotatedMethod(
				@ToolParam(required = false, description = "The username of the customer") String username,
				@ToolParam(required = true) String password) {
		}

		public void anotherAnnotatedMethod(String username, @ToolParam String password) {
		}

		public void openApiMethod(
				@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
						description = "The username of the customer") String username,
				@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password) {
		}

		public void jacksonMethod(
				@JsonProperty @JsonPropertyDescription("The username of the customer") String username,
				@JsonProperty(required = true) String password) {
		}

		public void nullableMethod(@Nullable String username, String password) {
		}

		public void complexMethod(List<String> items, TestData data, MoreTestData moreData) {
		}

		public void timeMethod(Duration duration, LocalDateTime localDateTime, Instant instant) {
		}

		public void contextMethod(String deliveryStatus, LocalDateTime expectedDelivery, ToolContext toolContext) {
		}

	}

	record TestData(int id, @ToolParam(description = "The special name") String name) {

	}

	@JsonClassDescription("Much more data")
	record MoreTestData(int id, @Schema(description = "Even more special name") String name) {

	}

	record AnnotatedPerson(@ToolParam int id, @ToolParam String name,
			@ToolParam(required = false, description = "The email of the person") String email) {

	}

	record JacksonPerson(@JsonProperty(required = true) int id, @JsonProperty(required = true) String name,
			@JsonProperty String email) {

	}

	record OpenApiPerson(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
					description = "The email of the person") String email) {

	}

	record NullablePerson(int id, String name, @Nullable String email) {

	}

	record JSpecifyNullablePerson(int id, String name, @org.jspecify.annotations.Nullable String email) {

	}

	static class Person {

		private int id;

		private String name;

		private String email;

		public int getId() {
			return this.id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getEmail() {
			return this.email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

	}

}
