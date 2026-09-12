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

package org.springframework.ai.tool.execution;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextToolCallResultConverter}.
 *
 * @author Iuliia Sobolevska
 */
class TextToolCallResultConverterTests {

	private final TextToolCallResultConverter converter = new TextToolCallResultConverter();

	@Test
	void convertStringReturnTypeShouldReturnText() {
		String result = this.converter.convert("test", String.class);

		assertThat(result).isEqualTo("test");
	}

	@Test
	void convertJsonStringReturnTypeShouldReturnText() {
		String result = this.converter.convert("{\"status\":\"ok\"}", String.class);

		assertThat(result).isEqualTo("{\"status\":\"ok\"}");
	}

	@Test
	void convertNullReturnValueShouldReturnNullJson() {
		String result = this.converter.convert(null, String.class);

		assertThat(result).isEqualTo("null");
	}

	@Test
	void convertVoidReturnTypeShouldReturnDoneJson() {
		String result = this.converter.convert(null, void.class);

		assertThat(result).isEqualTo("\"Done\"");
	}

	@Test
	void convertCollectionReturnTypeShouldReturnJson() {
		List<String> testList = List.of("one", "two", "three");
		String result = this.converter.convert(testList, List.class);

		assertThat(result).isEqualTo("""
				["one","two","three"]
				""".trim());
	}

	@Test
	void convertMapReturnTypeShouldReturnJson() {
		Map<String, Integer> testMap = Map.of("one", 1, "two", 2);
		String result = this.converter.convert(testMap, Map.class);

		assertThat(result).containsIgnoringWhitespaces("""
				"one": 1
				""").containsIgnoringWhitespaces("""
				"two": 2
				""");
	}

}
