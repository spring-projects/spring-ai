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

package org.springframework.ai.util.json;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.JsonHelper;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonSchemaGenerator}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class JsonSchemaGeneratorTests {

	private static final JsonHelper jsonHelper = new JsonHelper();

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

		JsonNode jsonNode = JacksonUtils.getDefaultJsonMapper().readTree(schema);
		assertThat(jsonNode.has("additionalProperties")).isFalse();
	}

	@Test
	void generateSchemaForMethodWithArraySchemaConstraints() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("arraySchemaConstraintsMethod", List.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);

		JsonNode tags = JacksonUtils.getDefaultJsonMapper().readTree(schema).path("properties").path("tags");
		assertThat(tags.path("type").asString()).isEqualTo("array");
		assertThat(tags.path("items").path("type").asString()).isEqualTo("string");
		assertThat(tags.path("minItems").asInt()).isEqualTo(1);
		assertThat(tags.path("maxItems").asInt()).isEqualTo(50);
		assertThat(tags.path("uniqueItems").asBoolean()).isTrue();
	}

	@Test
	void generateSchemaForMethodWithStringSchemaConstraints() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("stringSchemaConstraintsMethod", String.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);

		JsonNode code = JacksonUtils.getDefaultJsonMapper().readTree(schema).path("properties").path("code");
		assertThat(code.path("type").asString()).isEqualTo("string");
		assertThat(code.path("minLength").asInt()).isEqualTo(3);
		assertThat(code.path("maxLength").asInt()).isEqualTo(20);
		assertThat(code.path("pattern").asString()).isEqualTo("[a-z]+");
	}

	@Test
	void generateSchemaForMethodWithNumberSchemaConstraints() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("numberSchemaConstraintsMethod", int.class, double.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);

		JsonNode root = JacksonUtils.getDefaultJsonMapper().readTree(schema);
		JsonNode score = root.path("properties").path("score");
		assertThat(score.path("minimum").asInt()).isEqualTo(0);
		assertThat(score.path("maximum").asInt()).isEqualTo(100);
		assertThat(score.path("multipleOf").asInt()).isEqualTo(5);

		JsonNode ratio = root.path("properties").path("ratio");
		assertThat(ratio.path("minimum").asInt()).isEqualTo(0);
		assertThat(ratio.has("maximum")).isFalse();
		assertThat(ratio.path("exclusiveMaximum").asInt()).isEqualTo(1);
	}

	@Test
	void methodParameterConstraintsMatchFieldConstraints() throws Exception {
		String fieldSchema = JsonSchemaGenerator.generateForType(ConstrainedTags.class);
		Method method = TestMethods.class.getDeclaredMethod("arraySchemaConstraintsMethod", List.class);
		String parameterSchema = JsonSchemaGenerator.generateForMethodInput(method);

		JsonNode fieldTags = JacksonUtils.getDefaultJsonMapper().readTree(fieldSchema).path("properties").path("tags");
		JsonNode parameterTags = JacksonUtils.getDefaultJsonMapper()
			.readTree(parameterSchema)
			.path("properties")
			.path("tags");
		assertThat(parameterTags).isEqualTo(fieldTags);
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
	void generateSchemaForMethodWithUpperCaseTypesInTrLocale() throws Exception {
		Locale defaultLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			Method method = TestMethods.class.getDeclaredMethod("simpleMethod", String.class, int.class);

			// The method "convertTypeValuesToUpperCase" will fail to correctly uppercase
			// STRING and INTEGER in turkish locale, resulting in STRİNG and İNTEGER
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
		finally {
			Locale.setDefault(defaultLocale);
		}
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
				            "required": [ "id", "name" ],
				            "additionalProperties": false
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
				            "description" : "Much more data",
				            "additionalProperties": false
				        }
				    },
				    "required": [ "items", "data", "moreData" ],
				    "additionalProperties": false
				}
				""";

		assertThat(schema).isEqualToIgnoringWhitespace(expectedJsonSchema);
	}

	@Test
	void generateSchemaForMethodWithComplexParametersAndAdditionalPropertiesAllowed() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("complexMethod", List.class, TestData.class,
				MoreTestData.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method,
				JsonSchemaGenerator.SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT);

		JsonNode jsonNode = JacksonUtils.getDefaultJsonMapper().readTree(schema);
		assertThat(jsonNode.has("additionalProperties")).isFalse();
		assertThat(jsonNode.get("properties").get("data").has("additionalProperties")).isFalse();
		assertThat(jsonNode.get("properties").get("moreData").has("additionalProperties")).isFalse();
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

		JsonNode jsonNode = JacksonUtils.getDefaultJsonMapper().readTree(schema);
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
	void generateSchemaForTypeWithMapFieldDoesNotForbidAdditionalProperties() {
		String schema = JsonSchemaGenerator.generateForType(WithMapField.class);
		JsonNode jsonNode = JacksonUtils.getDefaultJsonMapper().readTree(schema);
		assertThat(jsonNode.get("additionalProperties").asBoolean())
			.as("root object schema should have additionalProperties: false")
			.isFalse();
		JsonNode scoresNode = jsonNode.get("properties").get("scores");
		assertThat(scoresNode.path("additionalProperties").asBoolean(true))
			.as("Map field must not have additionalProperties set to false")
			.isTrue();
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
				    ]
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
	void generateSchemaForMethodWithRecursiveParameter_defsAppearsBeforeProperties() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("searchBooksMethod", SearchRequest.class);

		String schemaJson = JsonSchemaGenerator.generateForMethodInput(method);

		assertThat(schemaJson.indexOf("\"$defs\"")).as("$defs must appear before properties in the serialized output")
			.isLessThan(schemaJson.indexOf("\"properties\""));
	}

	@Test
	void generateSchemaForMethodWithSimpleParameters_noDefsInOutput() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("simpleMethod", String.class, int.class);

		String schemaJson = JsonSchemaGenerator.generateForMethodInput(method);

		assertThat(schemaJson).as("no $defs placeholder must appear for non-recursive types").doesNotContain("$defs");
	}

	// gh-5888: when a method parameter type transitively contains a recursive type,
	// victools emits $defs nested inside the parameter sub-schema while $ref values
	// remain root-relative ("#/$defs/<Name>"). Inlining the sub-schema under
	// properties.<paramName> would otherwise leave those $refs unresolvable.
	// The generator must hoist $defs to the outer schema root.
	@Test
	void generateSchemaForMethodWithTransitivelyRecursiveParameterTypeHoistsDefs() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("searchBooksMethod", SearchRequest.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.has("$defs")).as("$defs must be hoisted to the outer schema root").isTrue();
		assertThat(schemaNode.get("$defs").has("RecursiveFilter")).isTrue();
		assertThat(schemaNode.at("/properties/request").has("$defs"))
			.as("$defs must not remain nested inside the parameter sub-schema")
			.isFalse();
		assertThat(schemaNode.at("/properties/request/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
		assertThat(schemaNode.at("/$defs/RecursiveFilter/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
	}

	// gh-5888: when two parameters share the same recursive type, the two
	// generated $defs entries collide on an identical key and value. The hoist
	// must reuse the single root entry; both parameters' $refs continue to
	// resolve to it.
	@Test
	void generateSchemaForMethodReusesRootDefsWhenCollidingDefsValuesAreEqual() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("sameOuterTwoParamsMethod", SearchRequest.class,
				SearchRequest.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs").size()).isEqualTo(1);
		assertThat(schemaNode.at("/$defs").has("RecursiveFilter")).isTrue();
		assertThat(schemaNode.at("/properties/a/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
		assertThat(schemaNode.at("/properties/b/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
	}

	// gh-5888: when two parameters carry different recursive types that share
	// the same simple class name, victools (with Option.PLAIN_DEFINITION_KEYS)
	// emits the same $defs key for both. First-wins would silently drop the
	// second definition and leave its $ref pointing at the first definition.
	// The hoist must rename the colliding entry and rewrite the inlined
	// sub-schema's $refs to point at the new key.
	@Test
	void generateSchemaForMethodRenamesDefsAndRewritesRefsOnSimpleNameCollision() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("collidingSimpleNameMethod", OuterA.SearchRequest.class,
				OuterB.SearchRequest.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs/Filter").has("properties")).isTrue();
		assertThat(schemaNode.at("/$defs/Filter_2").has("properties")).isTrue();
		assertThat(schemaNode.at("/$defs/Filter/properties").has("label"))
			.as("first colliding entry retains OuterA.Filter shape (label field)")
			.isTrue();
		assertThat(schemaNode.at("/$defs/Filter_2/properties").has("code"))
			.as("second colliding entry retains OuterB.Filter shape (code field)")
			.isTrue();
		assertThat(schemaNode.at("/properties/a/properties/filters/items/$ref").asString()).isEqualTo("#/$defs/Filter");
		assertThat(schemaNode.at("/properties/b/properties/filters/items/$ref").asString())
			.as("second parameter's $ref must be rewritten to the renamed entry")
			.isEqualTo("#/$defs/Filter_2");
		assertThat(schemaNode.at("/$defs/Filter/properties/children/items/$ref").asString())
			.isEqualTo("#/$defs/Filter");
		assertThat(schemaNode.at("/$defs/Filter_2/properties/children/items/$ref").asString())
			.as("self-reference inside the renamed entry must follow the rename")
			.isEqualTo("#/$defs/Filter_2");
	}

	// gh-5888: when a sub-schema brings in several $defs entries and one of them
	// collides while a peer entry references the colliding key, the peer's $ref
	// must be rewritten too — otherwise the peer would point at the existing
	// root entry instead of the renamed one.
	@Test
	void generateSchemaForMethodRewritesPeerDefinitionRefsAfterRename() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("peerReferenceMethod", PeerA.SearchRequest.class,
				PeerB.SearchRequest.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs/Filter/properties").has("label")).as("first definition keeps PeerA shape")
			.isTrue();
		assertThat(schemaNode.at("/$defs/Filter_2/properties").has("code"))
			.as("colliding definition is renamed with PeerB shape")
			.isTrue();
		assertThat(schemaNode.at("/$defs/Wrapper").has("properties")).isTrue();
		assertThat(schemaNode.at("/$defs/Wrapper/properties/filters/items/$ref").asString())
			.as("peer Wrapper's $ref to the colliding name must be rewritten to the renamed entry")
			.isEqualTo("#/$defs/Filter_2");
		assertThat(schemaNode.at("/$defs/Wrapper/properties/nested/items/$ref").asString())
			.as("peer Wrapper's self-reference must be left alone")
			.isEqualTo("#/$defs/Wrapper");
		assertThat(schemaNode.at("/$defs/Filter_2/properties/children/items/$ref").asString())
			.as("renamed entry's self-reference must follow the rename")
			.isEqualTo("#/$defs/Filter_2");
	}

	// gh-5888: forbidAdditionalProperties walks the whole schema tree including the
	// hoisted $defs block, so each definition that has a "properties" key must also
	// receive "additionalProperties": false.
	@Test
	void generateSchemaForMethodWithRecursiveTypeAppliesAdditionalPropertiesFalseToDefsEntries() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("searchBooksMethod", SearchRequest.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs/RecursiveFilter/additionalProperties").asBoolean(true))
			.as("additionalProperties: false must be propagated into hoisted $defs entries")
			.isFalse();
	}

	// gh-5888: ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT must suppress additionalProperties
	// enforcement everywhere, including inside hoisted $defs entries.
	@Test
	void generateSchemaForMethodWithRecursiveTypeAndAllowAdditionalPropertiesOption() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("searchBooksMethod", SearchRequest.class);

		String schema = JsonSchemaGenerator.generateForMethodInput(method,
				JsonSchemaGenerator.SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.has("$defs")).isTrue();
		assertThat(schemaNode.at("/$defs/RecursiveFilter").has("additionalProperties"))
			.as("ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT must not add additionalProperties to hoisted $defs entries")
			.isFalse();
		assertThat(schemaNode.has("additionalProperties"))
			.as("ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT must not add additionalProperties to root schema")
			.isFalse();
	}

	@Test
	void generateSchemaForTypeCanRunConcurrently() throws Exception {
		List<String> schemas = generateConcurrently(() -> JsonSchemaGenerator.generateForType(OrderedStatement.class));

		assertThat(schemas).hasSize(240);
		assertThat(schemas).allSatisfy(schema -> assertThat(schema).contains("\"properties\""));
	}

	@Test
	void generateSchemaForMethodInputCanRunConcurrently() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("searchBooksMethod", SearchRequest.class);

		List<String> schemas = generateConcurrently(() -> JsonSchemaGenerator.generateForMethodInput(method));

		assertThat(schemas).hasSize(240);
		assertThat(schemas).allSatisfy(schema -> assertThat(schema).contains("\"$defs\""));
	}

	@Test
	void throwExceptionWhenTypeIsNull() {
		assertThatThrownBy(() -> JsonSchemaGenerator.generateForType(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	private static List<String> generateConcurrently(Callable<String> generator) throws Exception {
		int threadCount = 12;
		int callCount = 240;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < callCount; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return generator.call();
				}));
			}
			start.countDown();
			List<String> schemas = new ArrayList<>();
			for (Future<String> future : futures) {
				schemas.add(future.get(30, TimeUnit.SECONDS));
			}
			return schemas;
		}
		finally {
			executor.shutdownNow();
		}
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

		public void searchBooksMethod(SearchRequest request) {
		}

		public void sameOuterTwoParamsMethod(SearchRequest a, SearchRequest b) {
		}

		public void collidingSimpleNameMethod(OuterA.SearchRequest a, OuterB.SearchRequest b) {
		}

		public void peerReferenceMethod(PeerA.SearchRequest a, PeerB.SearchRequest b) {
		}

		public void arraySchemaConstraintsMethod(
				@ArraySchema(minItems = 1, maxItems = 50, uniqueItems = true) List<String> tags) {
		}

		public void stringSchemaConstraintsMethod(
				@Schema(minLength = 3, maxLength = 20, pattern = "[a-z]+") String code) {
		}

		public void numberSchemaConstraintsMethod(@Schema(minimum = "0", maximum = "100", multipleOf = 5) int score,
				@Schema(minimum = "0", maximum = "1", exclusiveMaximum = true) double ratio) {
		}

	}

	record RecursiveFilter(String field, String operator, List<RecursiveFilter> filters) {
	}

	record SearchRequest(List<RecursiveFilter> filters, int limit) {
	}

	static class OuterA {

		record Filter(String label, List<Filter> children) {
		}

		record SearchRequest(List<Filter> filters, int limit) {
		}

	}

	static class OuterB {

		record Filter(String code, List<Filter> children) {
		}

		record SearchRequest(List<Filter> filters, int limit) {
		}

	}

	static class PeerA {

		record Filter(String label, List<Filter> children) {
		}

		record SearchRequest(List<Filter> filters) {
		}

	}

	static class PeerB {

		record Filter(String code, List<Filter> children) {
		}

		record Wrapper(List<Filter> filters, List<Wrapper> nested) {
		}

		record SearchRequest(Wrapper wrapper) {
		}

	}

	record TestData(int id, @ToolParam(description = "The special name") String name) {

	}

	record ConstrainedTags(@ArraySchema(minItems = 1, maxItems = 50, uniqueItems = true) List<String> tags) {

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

	record JSpecifyNullablePerson(int id, String name, @Nullable String email) {

	}

	record WithMapField(String name, Map<String, Integer> scores) {

	}

	@JsonPropertyOrder({ "accountId", "accountName", "currency", "totals" })
	record OrderedStatement(@JsonProperty(required = true) String accountId,
			@JsonProperty(required = true) String accountName, String currency, Map<String, Double> totals) {
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
