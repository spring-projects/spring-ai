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
package org.springframework.ai.parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Sebastian Ullrich
 * @author Kirk Lund
 */
@ExtendWith(MockitoExtension.class)
class BeanOutputParserTest {

	@Mock
	private ObjectMapper objectMapperMock;

	@Test
	public void shouldHavePreconfiguredDefaultObjectMapper() {
		var parser = new BeanOutputParser<>(TestClass.class);
		var objectMapper = parser.getObjectMapper();
		assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
	}

	@Test
	public void shouldUseProvidedObjectMapperForParsing() throws JsonProcessingException {
		var testClass = new TestClass("some string");
		when(objectMapperMock.readValue(anyString(), eq(TestClass.class))).thenReturn(testClass);
		var parser = new BeanOutputParser<>(TestClass.class, objectMapperMock);
		assertThat(parser.parse("{}")).isEqualTo(testClass);
	}

	@Nested
	class ParserTest {

		@Test
		public void shouldParseFieldNamesFromString() {
			var parser = new BeanOutputParser<>(TestClass.class);
			var testClass = parser.parse("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

		@Test
		public void shouldParseJsonPropertiesFromString() {
			var parser = new BeanOutputParser<>(TestClassWithJsonAnnotations.class);
			var testClass = parser.parse("{ \"string_property\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}

	}

	@Nested
	class FormatTest {

		@Test
		public void shouldReturnFormatContainingResponseInstructionsAndJsonSchema() {
			var parser = new BeanOutputParser<>(TestClass.class);
			assertThat(parser.getFormat()).isEqualTo(
					"""
							Your response should be in JSON format.
							Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
							Do not include markdown code blocks in your response.
							Here is the JSON Schema instance your output must adhere to:
							```{
							  "$schema" : "https://json-schema.org/draft/2020-12/schema",
							  "type" : "object",
							  "properties" : {
							    "someString" : {
							      "type" : "string"
							    }
							  }
							}```
							""");
		}

		@Test
		public void shouldReturnFormatContainingJsonSchemaIncludingPropertyAndPropertyDescription() {
			var parser = new BeanOutputParser<>(TestClassWithJsonAnnotations.class);
			assertThat(parser.getFormat()).contains("""
					```{
					  "$schema" : "https://json-schema.org/draft/2020-12/schema",
					  "type" : "object",
					  "properties" : {
					    "string_property" : {
					      "type" : "string",
					      "description" : "string_property_description"
					    }
					  }
					}```
					""");
		}

		@Test
		void normalizesLineEndings() {
			BeanOutputParser<TestClass> parser = new BeanOutputParser<>(TestClass.class);

			String formatOutput = parser.getFormat();

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