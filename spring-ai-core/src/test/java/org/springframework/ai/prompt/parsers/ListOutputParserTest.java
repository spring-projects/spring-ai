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
package org.springframework.ai.prompt.parsers;

import org.junit.jupiter.api.Test;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListOutputParserTest {

	@Test
	void csv() {
		String csvAsString = "foo, bar, baz";
		ListOutputParser listOutputParser = new ListOutputParser(new DefaultConversionService());
		List<String> list = listOutputParser.parse(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("foo", "bar", "baz"));
	}

}