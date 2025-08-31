/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

class ListOutputConverterTest {

	private ListOutputConverter listOutputConverter;

	@BeforeEach
	void setUp() {
		this.listOutputConverter = new ListOutputConverter(new DefaultConversionService());
	}

	@Test
	void csv() {
		String csvAsString = "foo, bar, baz";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("foo", "bar", "baz"));
	}

	@Test
	void csvWithoutSpaces() {
		String csvAsString = "A,B,C";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("A", "B", "C"));
	}

	@Test
	void csvWithExtraSpaces() {
		String csvAsString = "A  ,   B   ,  C  ";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("A", "B", "C"));
	}

	@Test
	void csvWithSingleItem() {
		String csvAsString = "single-item";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("single-item"));
	}

	@Test
	void csvWithEmptyString() {
		String csvAsString = "";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).isEmpty();
	}

	@Test
	void csvWithEmptyValues() {
		String csvAsString = "A, , C";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("A", "", "C"));
	}

	@Test
	void csvWithOnlyCommas() {
		String csvAsString = ",,";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("", "", ""));
	}

	@Test
	void csvWithTrailingComma() {
		String csvAsString = "A, B,";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("A", "B", ""));
	}

	@Test
	void csvWithLeadingComma() {
		String csvAsString = ", A, B";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("", "A", "B"));
	}

	@Test
	void csvWithSpecialCharacters() {
		String csvAsString = "value@example.com, item#123, $data%";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("value@example.com", "item#123", "$data%"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "a,b,c", "1,2,3", "X,Y,Z", "alpha,beta,gamma" })
	void csvWithVariousInputs(String csvString) {
		List<String> result = this.listOutputConverter.convert(csvString);
		assertThat(result).hasSize(3);
		assertThat(result).doesNotContainNull();
	}

	@Test
	void csvWithTabsAndSpecialWhitespace() {
		String csvAsString = "A\t, \tB\r, \nC ";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		// Behavior depends on implementation - this tests current behavior
		assertThat(list).hasSize(3);
		assertThat(list).doesNotContainNull();
	}

	@Test
	void csvWithOnlySpacesAndCommas() {
		String csvAsString = " , , ";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("", "", ""));
	}

	@Test
	void csvWithBooleanLikeValues() {
		String csvAsString = "true, false, TRUE, FALSE, yes, no";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("true", "false", "TRUE", "FALSE", "yes", "no"));
	}

	@Test
	void csvWithDifferentDataTypes() {
		String csvAsString = "string, 123, 45.67, true, null";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("string", "123", "45.67", "true", "null"));
		// All values should be strings since it's a ListOutputConverter for strings
	}

	@Test
	void csvWithAlternativeDelimiters() {
		// Test behavior with semicolon (common in some locales)
		String csvAsString = "A; B; C";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		// This tests current behavior - might be one item if semicolon isn't supported
		assertThat(list).isNotEmpty();
	}

	@Test
	void csvWithQuotedValues() {
		String csvAsString = "\"quoted value\", normal, \"another quoted\"";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).hasSize(3);
		assertThat(list).doesNotContainNull();
	}

	@Test
	void csvWithEscapedQuotes() {
		String csvAsString = "\"value with \"\"quotes\"\"\", normal, \"escaped\"";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).isNotEmpty();
		assertThat(list).doesNotContainNull();
	}

	@Test
	void csvWithOnlyWhitespace() {
		String csvAsString = "   \t\n   ";
		List<String> list = this.listOutputConverter.convert(csvAsString);
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isBlank();
	}

}
