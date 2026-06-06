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

package org.springframework.ai.converter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.TextBlockAssertion;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Konstantin Pavlov
 */
class BeanOutputConverterTest {

	@Test
	void shouldHavePreConfiguredDefaultObjectMapper() {
		var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

		});
		var jsonMapper = converter.getJsonMapper();
		assertThat(jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
	}

	static class TestClass {

		private String someString;

		TestClass() {
		}

		String getSomeString() {
			return this.someString;
		}

		@SuppressWarnings("unused")
		void setSomeString(String someString) {
			this.someString = someString;
		}

	}

	static class TestClassWithDateProperty {

		private LocalDate someString;

		TestClassWithDateProperty() {
		}

		LocalDate getSomeString() {
			return this.someString;
		}

		@SuppressWarnings("unused")
		void setSomeString(LocalDate someString) {
			this.someString = someString;
		}

	}

	static class TestClassWithJsonAnnotations {

		@JsonProperty("string_property")
		@JsonPropertyDescription("string_property_description")
		private String someString;

		TestClassWithJsonAnnotations() {
		}

		String getSomeString() {
			return this.someString;
		}

	}

	@JsonPropertyOrder({ "string_property", "foo_property", "bar_property" })
	record TestClassWithJsonPropertyOrder(
			@JsonProperty("string_property") @JsonPropertyDescription("string_property_description") String someString,

			@JsonProperty(required = true, value = "bar_property") String bar,

			@JsonProperty(required = true, value = "foo_property") String foo) {
	}

	record TestClassWithToolParam(@ToolParam(required = true, description = "A required field") String requiredField,

			@ToolParam(required = false, description = "An optional field") String optionalField) {
	}

	record TestClassWithNullable(String requiredField, @Nullable String optionalField) {
	}

	@Nested
	class ConverterTest {

		@Test
		void convertClassType() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			var testClass = converter.convert("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertClassWithDateType() {
			var converter = new BeanOutputConverter<>(TestClassWithDateProperty.class);
			var testClass = converter.convert("{ \"someString\": \"2020-01-01\" }");
			assertThat(testClass.getSomeString()).isEqualTo(LocalDate.of(2020, 1, 1));
		}

		@Test
		void convertTypeReference() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

			});
			var testClass = converter.convert("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertTypeReferenceArray() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TestClass>>() {

			});
			List<TestClass> testClass = converter.convert("[{ \"someString\": \"some value\" }]");
			assertThat(testClass).hasSize(1);
			assertThat(testClass.get(0).getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertClassTypeWithJsonAnnotations() {
			var converter = new BeanOutputConverter<>(TestClassWithJsonAnnotations.class);
			var testClass = converter.convert("{ \"string_property\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void verifySchemaPropertyOrder() throws Exception {
			var converter = new BeanOutputConverter<>(TestClassWithJsonPropertyOrder.class);
			String jsonSchema = converter.getJsonSchema();

			JsonNode schemaNode = JsonMapper.shared().readTree(jsonSchema);

			List<String> actualOrder = new ArrayList<>(schemaNode.get("properties").propertyNames());

			assertThat(actualOrder).containsExactly("string_property", "foo_property", "bar_property");
		}

		@Test
		void convertTypeReferenceWithJsonAnnotations() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClassWithJsonAnnotations>() {

			});
			var testClass = converter.convert("{ \"string_property\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertTypeReferenceArrayWithJsonAnnotations() {
			var converter = new BeanOutputConverter<>(
					new ParameterizedTypeReference<List<TestClassWithJsonAnnotations>>() {

					});
			List<TestClassWithJsonAnnotations> testClass = converter
				.convert("[{ \"string_property\": \"some value\" }]");
			assertThat(testClass).hasSize(1);
			assertThat(testClass.get(0).getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertWithThinkingTags() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithThinkingTags = "<thinking>This is my reasoning process...</thinking>{ \"someString\": \"some value\" }";
			var testClass = converter.convert(textWithThinkingTags);
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertWithThinkingTagsMultiline() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithThinkingTags = """
					<thinking>
					This is my reasoning process
					spanning multiple lines
					</thinking>
					{ "someString": "some value" }
					""";
			var testClass = converter.convert(textWithThinkingTags);
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertWithThinkingTagsAndMarkdownCodeBlock() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithThinkingTags = """
					<thinking>This is my reasoning process...</thinking>
					```json
					{ "someString": "some value" }
					```
					""";
			var testClass = converter.convert(textWithThinkingTags);
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertWithMultipleThinkingTags() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithThinkingTags = "<thinking>First thought</thinking><thinking>Second thought</thinking>{ \"someString\": \"some value\" }";
			var testClass = converter.convert(textWithThinkingTags);
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void convertWithQwenThinkTags() {
			// Test Qwen model format: <think>...</think>
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithThinkTags = "<think>Let me analyze this...</think>{ \"someString\": \"qwen test\" }";
			var testClass = converter.convert(textWithThinkTags);
			assertThat(testClass.getSomeString()).isEqualTo("qwen test");
		}

		@Test
		void convertWithQwenThinkTagsMultiline() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithThinkTags = """
					<think>
					Analyzing the request step by step
					First, I need to understand the schema
					Then generate the JSON
					</think>
					{ "someString": "qwen multiline" }
					""";
			var testClass = converter.convert(textWithThinkTags);
			assertThat(testClass.getSomeString()).isEqualTo("qwen multiline");
		}

		@Test
		void convertWithMixedThinkingAndThinkTags() {
			// Test mixed format from different models
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithMixedTags = "<thinking>Nova reasoning</thinking><think>Qwen analysis</think>{ \"someString\": \"mixed test\" }";
			var testClass = converter.convert(textWithMixedTags);
			assertThat(testClass.getSomeString()).isEqualTo("mixed test");
		}

		@Test
		void convertWithReasoningTags() {
			// Test alternative reasoning tags
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithReasoningTags = "<reasoning>Internal reasoning process</reasoning>{ \"someString\": \"reasoning test\" }";
			var testClass = converter.convert(textWithReasoningTags);
			assertThat(testClass.getSomeString()).isEqualTo("reasoning test");
		}

		@Test
		void convertWithMarkdownThinkingBlock() {
			// Test markdown-style thinking block
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithMarkdownThinking = """
					```thinking
					This is a markdown-style thinking block
					Used by some models
					```
					{ "someString": "markdown thinking" }
					""";
			var testClass = converter.convert(textWithMarkdownThinking);
			assertThat(testClass.getSomeString()).isEqualTo("markdown thinking");
		}

		@Test
		void convertWithCaseInsensitiveTags() {
			// Test case insensitive tag matching
			var converter = new BeanOutputConverter<>(TestClass.class);
			String textWithUpperCaseTags = "<THINKING>UPPERCASE THINKING</THINKING>{ \"someString\": \"case test\" }";
			var testClass = converter.convert(textWithUpperCaseTags);
			assertThat(testClass.getSomeString()).isEqualTo("case test");
		}

		@Test
		void convertWithComplexNestedStructure() {
			// Test complex scenario with multiple formats combined
			var converter = new BeanOutputConverter<>(TestClass.class);
			String complexText = """
					<thinking>Nova model reasoning</thinking>
					<think>Qwen model analysis</think>

					```json
					{ "someString": "complex test" }
					```
					""";
			var testClass = converter.convert(complexText);
			assertThat(testClass.getSomeString()).isEqualTo("complex test");
		}

	}

	// @checkstyle:off RegexpSinglelineJavaCheck
	@Nested
	class FormatTest {

		@Test
		void formatClassType() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			TextBlockAssertion.assertThat(converter.getFormat())
				.isEqualTo(
						"""
								Your response should be in JSON format.
								Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
								Do not include markdown code blocks in your response.
								Remove the ```json markdown from the output.
								Here is the JSON Schema instance your output must adhere to:
								```{
								  "$schema" : "https://json-schema.org/draft/2020-12/schema",
								  "type" : "object",
								  "properties" : {
								    "someString" : {
								      "type" : "string"
								    }
								  },
								  "required" : [ "someString" ],
								  "additionalProperties" : false
								}```
								""");
		}

		@Test
		void formatTypeReference() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

			});
			TextBlockAssertion.assertThat(converter.getFormat())
				.isEqualTo(
						"""
								Your response should be in JSON format.
								Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
								Do not include markdown code blocks in your response.
								Remove the ```json markdown from the output.
								Here is the JSON Schema instance your output must adhere to:
								```{
								  "$schema" : "https://json-schema.org/draft/2020-12/schema",
								  "type" : "object",
								  "properties" : {
								    "someString" : {
								      "type" : "string"
								    }
								  },
								  "required" : [ "someString" ],
								  "additionalProperties" : false
								}```
								""");
		}

		@Test
		void formatTypeReferenceArray() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TestClass>>() {

			});
			TextBlockAssertion.assertThat(converter.getFormat())
				.isEqualTo(
						"""
								Your response should be in JSON format.
								Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
								Do not include markdown code blocks in your response.
								Remove the ```json markdown from the output.
								Here is the JSON Schema instance your output must adhere to:
								```{
								  "$schema" : "https://json-schema.org/draft/2020-12/schema",
								  "type" : "array",
								  "items" : {
								    "type" : "object",
								    "properties" : {
								      "someString" : {
								        "type" : "string"
								      }
								    },
								    "required" : [ "someString" ],
								    "additionalProperties" : false
								  }
								}```
								""");
		}

		@Test
		void formatClassTypeWithAnnotations() {
			var converter = new BeanOutputConverter<>(TestClassWithJsonAnnotations.class);
			TextBlockAssertion.assertThat(converter.getFormat()).contains("""
					```{
					  "$schema" : "https://json-schema.org/draft/2020-12/schema",
					  "type" : "object",
					  "properties" : {
					    "string_property" : {
					      "type" : "string",
					      "description" : "string_property_description"
					    }
					  },
					  "additionalProperties" : false
					}```
					""");
		}

		@Test
		void formatTypeReferenceWithAnnotations() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClassWithJsonAnnotations>() {

			});
			TextBlockAssertion.assertThat(converter.getFormat()).contains("""
					```{
					  "$schema" : "https://json-schema.org/draft/2020-12/schema",
					  "type" : "object",
					  "properties" : {
					    "string_property" : {
					      "type" : "string",
					      "description" : "string_property_description"
					    }
					  },
					  "additionalProperties" : false
					}```
					""");
		}
		// @checkstyle:on RegexpSinglelineJavaCheck

		@Test
		void formatClassTypeWithToolParamAnnotations() {
			var converter = new BeanOutputConverter<>(TestClassWithToolParam.class);
			String schema = converter.getJsonSchema();
			JsonNode schemaNode = JsonMapper.shared().readTree(schema);

			assertThat(schemaNode.get("required").toString()).contains("requiredField");
			assertThat(schemaNode.get("required").toString()).doesNotContain("optionalField");

			assertThat(schemaNode.get("properties").get("requiredField").get("description").asString())
				.isEqualTo("A required field");
			assertThat(schemaNode.get("properties").get("optionalField").get("description").asString())
				.isEqualTo("An optional field");
		}

		@Test
		void formatClassTypeWithNullableAnnotation() {
			var converter = new BeanOutputConverter<>(TestClassWithNullable.class);
			String schema = converter.getJsonSchema();
			JsonNode schemaNode = JsonMapper.shared().readTree(schema);

			assertThat(schemaNode.get("required").toString()).contains("requiredField");
			assertThat(schemaNode.get("required").toString()).doesNotContain("optionalField");
		}

		@Test
		void formatUsesCustomGeneratedSchema() {
			var converter = new BeanOutputConverter<>(TestClass.class) {

				@Override
				protected String generateSchema() {
					return """
							{
							  "type" : "object",
							  "properties" : {
							    "customProperty" : {
							      "type" : "string"
							    }
							  }
							}
							""";
				}
			};

			assertThat(converter.getJsonSchema()).contains("\"customProperty\"");
			assertThat(converter.getJsonSchema()).doesNotContain("\"someString\"");
			assertThat(converter.getFormat()).contains("\"customProperty\"");
		}

	}

}
