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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonSurroundingTextCleaner}.
 *
 * @author welikeWatermelon
 */
class JsonSurroundingTextCleanerTest {

	@Test
	void shouldRemovePreambleBeforeJsonObject() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "I'll analyze and evaluate that for you. Here are the results\n{\n  \"result\" : \"PASS\"\n}";
		assertThat(cleaner.clean(input)).isEqualTo("{\n  \"result\" : \"PASS\"\n}");
	}

	@Test
	void shouldRemoveTrailingTextAfterJsonObject() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "{\"result\":\"PASS\"}\nHope this helps!";
		assertThat(cleaner.clean(input)).isEqualTo("{\"result\":\"PASS\"}");
	}

	@Test
	void shouldRemovePreambleAndMarkdownCodeBlock() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "Here you go:\n```json\n{\"result\":\"PASS\"}\n```";
		assertThat(cleaner.clean(input)).isEqualTo("{\"result\":\"PASS\"}");
	}

	@Test
	void shouldSkipBracesInPreamble() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "Here is {the} result: {\"result\":\"PASS\"}";
		assertThat(cleaner.clean(input)).isEqualTo("{\"result\":\"PASS\"}");
	}

	@Test
	void shouldIgnoreBracesAndEscapedQuotesInsideStrings() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String json = "{\"a\": \"x}y \\\"z\\\"\"}";
		assertThat(cleaner.clean("Result: " + json + " Done.")).isEqualTo(json);
	}

	@Test
	void shouldKeepTopLevelArray() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String json = "[ {\"result\":\"PASS\"}, {\"result\":\"FAIL\"} ]";
		assertThat(cleaner.clean("Here are both results:\n" + json + "\nLet me know!")).isEqualTo(json);
	}

	@Test
	void shouldNotModifyTextWithoutJson() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "Sorry, I cannot evaluate that.";
		assertThat(cleaner.clean(input)).isSameAs(input);
	}

	@Test
	void shouldNotModifyUnbalancedJson() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "Here are the results: {\"result\":\"PASS\"";
		assertThat(cleaner.clean(input)).isSameAs(input);
	}

	@Test
	void shouldNotExtractNestedObjectFromTruncatedJson() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "{\"result\":\"PASS\",\"nested\":{\"a\":\"b\"},\"other\":";
		assertThat(cleaner.clean(input)).isSameAs(input);
	}

	@Test
	void shouldNotExtractNestedObjectFromMismatchedBrackets() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String input = "Here: {\"a\":{\"b\":1}]";
		assertThat(cleaner.clean(input)).isSameAs(input);
	}

	@Test
	void shouldNotModifyCleanJson() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String object = "{\"result\":\"PASS\",\"nested\":{\"items\":[1,2]}}";
		String array = "[{\"result\":\"PASS\"}]";
		assertThat(cleaner.clean(object)).isSameAs(object);
		assertThat(cleaner.clean(array)).isSameAs(array);
	}

	@Test
	void shouldNotModifyJsonScalars() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		String string = "\"a value with {braces}\"";
		String number = "42";
		assertThat(cleaner.clean(string)).isSameAs(string);
		assertThat(cleaner.clean(number)).isSameAs(number);
	}

	@Test
	void shouldReturnNullForNullInput() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		assertThat(cleaner.clean(null)).isNull();
	}

	@Test
	void shouldReturnEmptyForEmptyInput() {
		JsonSurroundingTextCleaner cleaner = new JsonSurroundingTextCleaner();
		assertThat(cleaner.clean("")).isEmpty();
		assertThat(cleaner.clean("   ")).isEqualTo("   ");
	}

}
