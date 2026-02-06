/*
 * Copyright 2023-2026 the original author or authors.
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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import org.springframework.ai.util.TextBlockAssertion;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.util.LoggingMarkers.SENSITIVE_DATA_MARKER;

/**
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Konstantin Pavlov
 */
@ExtendWith(MockitoExtension.class)
class BeanOutputConverterTest {

	private ListAppender<ILoggingEvent> logAppender;

	@Mock
	private ObjectMapper objectMapperMock;

	@BeforeEach
	void beforeEach() {

		var logger = (Logger) LoggerFactory.getLogger(BeanOutputConverter.class);

		this.logAppender = new ListAppender<>();
		this.logAppender.start();
		logger.addAppender(this.logAppender);
	}

	@Test
	void shouldHavePreConfiguredDefaultObjectMapper() {
		var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

		});
		var objectMapper = converter.getObjectMapper();
		assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
	}

	static class TestClass {

		private String someString;

		@SuppressWarnings("unused")
		TestClass() {
		}

		TestClass(String someString) {
			this.someString = someString;
		}

		String getSomeString() {
			return this.someString;
		}

		public void setSomeString(String someString) {
			this.someString = someString;
		}

	}

	static class TestClassWithDateProperty {

		private LocalDate someString;

		@SuppressWarnings("unused")
		TestClassWithDateProperty() {
		}

		TestClassWithDateProperty(LocalDate someString) {
			this.someString = someString;
		}

		LocalDate getSomeString() {
			return this.someString;
		}

		public void setSomeString(LocalDate someString) {
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

	@Nested
	class ConverterTest {

		@Test
		void convertClassType() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			var testClass = converter.convert("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		void failToConvertInvalidJson() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			assertThatThrownBy(() -> converter.convert("{invalid json")).hasCauseInstanceOf(JsonParseException.class);
			assertThat(BeanOutputConverterTest.this.logAppender.list).hasSize(1);
			final var loggingEvent = BeanOutputConverterTest.this.logAppender.list.get(0);
			assertThat(loggingEvent.getFormattedMessage())
				.isEqualTo("Could not parse the given text to the desired target type: \"{invalid json\" into "
						+ TestClass.class);

			assertThat(loggingEvent.getMarkerList()).contains(SENSITIVE_DATA_MARKER);
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

			ObjectMapper mapper = new ObjectMapper();
			JsonNode schemaNode = mapper.readTree(jsonSchema);

			List<String> actualOrder = new ArrayList<>();
			schemaNode.get("properties").fieldNames().forEachRemaining(actualOrder::add);

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
		void normalizesLineEndingsClassType() {
			var converter = new BeanOutputConverter<>(TestClass.class);

			String formatOutput = converter.getFormat();

			// validate that output contains \n line endings
			assertThat(formatOutput).contains(System.lineSeparator()).doesNotContain("\r\n").doesNotContain("\r");
		}

		@Test
		void normalizesLineEndingsTypeReference() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

			});

			String formatOutput = converter.getFormat();

			// validate that output contains \n line endings
			assertThat(formatOutput).contains(System.lineSeparator()).doesNotContain("\r\n").doesNotContain("\r");
		}

	}

	@Nested
	class SchemaAnnotationTest {

		@Test
		void schemaDescriptionSupport() {
			record PersonWithSchema(@Schema(description = "Person ID") int id,
					@Schema(description = "Person name") String name) {
			}

			var converter = new BeanOutputConverter<>(PersonWithSchema.class);
			String schema = converter.getJsonSchema();

			assertThat(schema).contains("\"description\" : \"Person ID\"");
			assertThat(schema).contains("\"description\" : \"Person name\"");
		}

		@Test
		void backwardCompatibilityWithJsonPropertyDescription() {
			record LegacyPerson(@JsonPropertyDescription("Name description") String name) {
			}

			var converter = new BeanOutputConverter<>(LegacyPerson.class);
			String schema = converter.getJsonSchema();

			assertThat(schema).contains("\"description\" : \"Name description\"");
		}

		@Test
		void schemaRequiredModeNotRequired() {
			record PersonWithOptionalEmail(@Schema(description = "Person ID") int id,
					@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
							description = "Optional email") String email) {
			}

			var converter = new BeanOutputConverter<>(PersonWithOptionalEmail.class);
			String schema = converter.getJsonSchema();

			assertThat(schema).contains("\"required\" : [ \"id\" ]");
			assertThat(schema).doesNotContain("\"required\" : [ \"id\", \"email\" ]");
		}

		@Test
		void jsonPropertyRequiredFalse() {
			record PersonWithOptionalField(int id, @JsonProperty(required = false) String optionalField) {
			}

			var converter = new BeanOutputConverter<>(PersonWithOptionalField.class);
			String schema = converter.getJsonSchema();

			assertThat(schema).contains("\"required\" : [ \"id\" ]");
			assertThat(schema).doesNotContain("\"required\" : [ \"id\", \"optionalField\" ]");
		}

		@Test
		void nullableAnnotation() {
			record PersonWithNullable(int id, @Nullable String optionalField) {
			}

			var converter = new BeanOutputConverter<>(PersonWithNullable.class);
			String schema = converter.getJsonSchema();

			assertThat(schema).contains("\"required\" : [ \"id\" ]");
			assertThat(schema).doesNotContain("\"required\" : [ \"id\", \"optionalField\" ]");
		}

		@Test
		void mixedRequiredAnnotations() {
			record PersonWithMixed(int id,
					@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String optionalViaSchema,
					@JsonProperty(required = false) String optionalViaJsonProperty, String requiredByDefault) {
			}

			var converter = new BeanOutputConverter<>(PersonWithMixed.class);
			String schema = converter.getJsonSchema();

			assertThat(schema).contains("\"required\" : [ \"id\", \"requiredByDefault\" ]");
			// Properties should be defined but not in required array
			assertThat(schema).contains("\"optionalViaSchema\" : {");
			assertThat(schema).contains("\"optionalViaJsonProperty\" : {");
		}

		@Test
		void backwardCompatibilityAllRequiredByDefault() {
			record SimpleRecord(int id, String name, String email) {
			}

			var converter = new BeanOutputConverter<>(SimpleRecord.class);
			String schema = converter.getJsonSchema();

			// Fields without annotations remain required by default (order may be
			// alphabetical)
			assertThat(schema).contains("\"required\" : [ \"email\", \"id\", \"name\" ]");
		}

	}

}
