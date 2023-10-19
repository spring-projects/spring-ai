package org.springframework.ai.parser;

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
		public void shouldParseFromString() {
			var parser = new BeanOutputParser<>(TestClass.class);
			var testClass = parser.parse("{ \"someString\": \"some value\" }");
			assertThat(testClass.getSomeString()).isEqualTo("some value");
		}
	}

	@Nested
	class FormatTest {

		@Test
		public void shouldReturnFormatContainingResponseInstructionsAndJsonSchema() {
			var parser = new BeanOutputParser<>(TestClass.class);
			assertThat(parser.getFormat()).isEqualTo("""
					Your response should be in JSON format.
					Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
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
}
