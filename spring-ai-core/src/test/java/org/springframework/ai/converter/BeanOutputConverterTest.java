/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.converter;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class BeanOutputConverterTest {

	@Mock
	private ObjectMapper objectMapperMock;

	@Test
	public void shouldHavePreConfiguredDefaultObjectMapper() {
		var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {
		});
		var objectMapper = converter.getObjectMapper();
		assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
	}

	@Nested
	class ConverterTest {

		@Test
		public void convertClassType() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			var testClass = converter.convert("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		public void convertClassWithDateType() {
			var converter = new BeanOutputConverter<>(TestClassWithDateProperty.class);
			var testClass = converter.convert("{ \"someString\": \"2020-01-01\" }");
			assertThat(testClass.getSomeString()).isEqualTo(LocalDate.of(2020, 1, 1));
		}

		@Test
		public void convertTypeReference() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {
			});
			var testClass = converter.convert("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		public void convertTypeReferenceArray() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TestClass>>() {
			});
			List<TestClass> testClass = converter.convert("[{ \"someString\": \"some value\" }]");
			assertThat(testClass).hasSize(1);
			assertThat(testClass.get(0).getSomeString()).isEqualTo("some value");
		}

		@Test
		public void convertClassTypeWithJsonAnnotations() {
			var converter = new BeanOutputConverter<>(TestClassWithJsonAnnotations.class);
			var testClass = converter.convert("{ \"string_property\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		public void convertTypeReferenceWithJsonAnnotations() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClassWithJsonAnnotations>() {
			});
			var testClass = converter.convert("{ \"string_property\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		public void convertTypeReferenceArrayWithJsonAnnotations() {
			var converter = new BeanOutputConverter<>(
					new ParameterizedTypeReference<List<TestClassWithJsonAnnotations>>() {
					});
			List<TestClassWithJsonAnnotations> testClass = converter
				.convert("[{ \"string_property\": \"some value\" }]");
			assertThat(testClass).hasSize(1);
			assertThat(testClass.get(0).getSomeString()).isEqualTo("some value");
		}

	}

	@Nested
	class FormatTest {

		@Test
		public void formatClassType() {
			var converter = new BeanOutputConverter<>(TestClass.class);
			assertThat(converter.getFormat()).isEqualTo(
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
							  "additionalProperties" : false
							}```
							""");
		}

		@Test
		public void formatTypeReference() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {
			});
			assertThat(converter.getFormat()).isEqualTo(
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
							  "additionalProperties" : false
							}```
							""");
		}

		@Test
		public void formatTypeReferenceArray() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TestClass>>() {
			});
			assertThat(converter.getFormat()).isEqualTo(
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
							    "additionalProperties" : false
							  }
							}```
							""");
		}

		@Test
		public void formatClassTypeWithAnnotations() {
			var converter = new BeanOutputConverter<>(TestClassWithJsonAnnotations.class);
			assertThat(converter.getFormat()).contains("""
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
		public void formatTypeReferenceWithAnnotations() {
			var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClassWithJsonAnnotations>() {
			});
			assertThat(converter.getFormat()).contains("""
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

	public static class TestClass {

		private String someString;

		@SuppressWarnings("unused")
		public TestClass() {
		}

		public TestClass(String someString) {
			this.someString = someString;
		}

		public String getSomeString() {
			return someString;
		}

	}

	public static class TestClassWithDateProperty {

		private LocalDate someString;

		@SuppressWarnings("unused")
		public TestClassWithDateProperty() {
		}

		public TestClassWithDateProperty(LocalDate someString) {
			this.someString = someString;
		}

		public LocalDate getSomeString() {
			return someString;
		}

	}

	public static class TestClassWithJsonAnnotations {

		@JsonProperty("string_property")
		@JsonPropertyDescription("string_property_description")
		private String someString;

		public TestClassWithJsonAnnotations() {
		}

		public String getSomeString() {
			return someString;
		}

	}

}