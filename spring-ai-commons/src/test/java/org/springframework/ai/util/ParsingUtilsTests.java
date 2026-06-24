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

package org.springframework.ai.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ParsingUtils}.
 *
 * @author gh7035
   */
class ParsingUtilsTests {

	@Test
  	void splitCamelCaseSingleWord() {
      		List<String> result = ParsingUtils.splitCamelCase("hello");
      		assertThat(result).containsExactly("hello");
    }

	@Test
  	void splitCamelCaseMultipleWords() {
      		List<String> result = ParsingUtils.splitCamelCase("helloWorld");
      		assertThat(result).containsExactly("hello", "World");
    }

	@Test
  	void splitCamelCaseThreeWords() {
      		List<String> result = ParsingUtils.splitCamelCase("helloWorldFoo");
      		assertThat(result).containsExactly("hello", "World", "Foo");
    }

	@Test
  	void splitCamelCaseToLowerSingleWord() {
      		List<String> result = ParsingUtils.splitCamelCaseToLower("hello");
      		assertThat(result).containsExactly("hello");
    }

	@Test
  	void splitCamelCaseToLowerMultipleWords() {
      		List<String> result = ParsingUtils.splitCamelCaseToLower("helloWorld");
      		assertThat(result).containsExactly("hello", "world");
    }

	@Test
  	void splitCamelCaseToLowerThreeWords() {
      		List<String> result = ParsingUtils.splitCamelCaseToLower("helloWorldFoo");
      		assertThat(result).containsExactly("hello", "world", "foo");
    }

	@Test
  	void reConcatenateCamelCaseWithDash() {
      		String result = ParsingUtils.reConcatenateCamelCase("helloWorld", "-");
      		assertThat(result).isEqualTo("hello-world");
    }

	@Test
  	void reConcatenateCamelCaseWithUnderscore() {
      		String result = ParsingUtils.reConcatenateCamelCase("helloWorldFoo", "_");
      		assertThat(result).isEqualTo("hello_world_foo");
    }

	@Test
  	void reConcatenateCamelCaseWithEmptyDelimiter() {
      		String result = ParsingUtils.reConcatenateCamelCase("helloWorld", "");
      		assertThat(result).isEqualTo("helloworld");
    }

	@Test
  	void splitCamelCaseNullSourceThrows() {
      		assertThatThrownBy(() -> ParsingUtils.splitCamelCase(null))
            			.isInstanceOf(IllegalArgumentException.class);
    }

	@Test
  	void splitCamelCaseToLowerNullSourceThrows() {
      		assertThatThrownBy(() -> ParsingUtils.splitCamelCaseToLower(null))
            			.isInstanceOf(IllegalArgumentException.class);
    }

	@Test
  	void reConcatenateCamelCaseNullSourceThrows() {
      		assertThatThrownBy(() -> ParsingUtils.reConcatenateCamelCase(null, "-"))
            			.isInstanceOf(IllegalArgumentException.class);
    }

	@Test
  	void reConcatenateCamelCaseNullDelimiterThrows() {
      		assertThatThrownBy(() -> ParsingUtils.reConcatenateCamelCase("helloWorld", null))
            			.isInstanceOf(IllegalArgumentException.class);
    }

}
