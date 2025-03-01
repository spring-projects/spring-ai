package org.springframework.ai.tool.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultToolCallResultConverter}.
 *
 * @author Thomas Vitale
 */
class DefaultToolCallResultConverterTests {

	private final DefaultToolCallResultConverter converter = new DefaultToolCallResultConverter();

	@Test
	void convertWithNullReturnTypeShouldReturn() {
		String result = converter.convert(null, null);
		assertThat(result).isEqualTo("null");
	}

	@Test
	void convertVoidReturnTypeShouldReturnDone() {
		String result = converter.convert(null, void.class);
		assertThat(result).isEqualTo("Done");
	}

	@Test
	void convertStringReturnTypeShouldReturnJson() {
		String result = converter.convert("test", String.class);
		assertThat(result).isEqualTo("\"test\"");
	}

	@Test
	void convertNullReturnValueShouldReturnNullJson() {
		String result = converter.convert(null, String.class);
		assertThat(result).isEqualTo("null");
	}

	@Test
	void convertObjectReturnTypeShouldReturnJson() {
		TestObject testObject = new TestObject("test", 42);
		String result = converter.convert(testObject, TestObject.class);
		assertThat(result).containsIgnoringWhitespaces("""
				"name": "test"
				""").containsIgnoringWhitespaces("""
				"value": 42
				""");
	}

	@Test
	void convertCollectionReturnTypeShouldReturnJson() {
		List<String> testList = List.of("one", "two", "three");
		String result = converter.convert(testList, List.class);
		assertThat(result).isEqualTo("""
				["one","two","three"]
				""".trim());
	}

	@Test
	void convertMapReturnTypeShouldReturnJson() {
		Map<String, Integer> testMap = Map.of("one", 1, "two", 2);
		String result = converter.convert(testMap, Map.class);
		assertThat(result).containsIgnoringWhitespaces("""
				"one": 1
				""").containsIgnoringWhitespaces("""
				"two": 2
				""");
	}

	static class TestObject {

		private final String name;

		private final int value;

		TestObject(String name, int value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public int getValue() {
			return value;
		}

	}

}
