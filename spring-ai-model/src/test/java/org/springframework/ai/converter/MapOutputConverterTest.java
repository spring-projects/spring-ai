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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MapOutputConverterTest {

	private MapOutputConverter mapOutputConverter;

	@BeforeEach
	void setUp() {
		this.mapOutputConverter = new MapOutputConverter();
	}

	@Test
	void plainJson() {
		assertThat(this.mapOutputConverter.convert("{\"name\":\"foo\"}")).containsExactly(Map.entry("name", "foo"));
	}

	/**
	 * Models wrap the payload in a code fence in a number of shapes: with or without a
	 * language tag, in either case, and with surrounding whitespace.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "```json\n{\"name\":\"foo\"}\n```", "\n```json\n{\"name\":\"foo\"}\n```\n",
			"```json\n{\"name\":\"foo\"}\n```   ", "```JSON\n{\"name\":\"foo\"}\n```", "```\n{\"name\":\"foo\"}\n```" })
	void fencedJson(String text) {
		assertThat(this.mapOutputConverter.convert(text)).containsExactly(Map.entry("name", "foo"));
	}

	@Test
	void thinkingTagIsStripped() {
		assertThat(this.mapOutputConverter.convert("<think>reasoning</think>\n{\"name\":\"foo\"}"))
			.containsExactly(Map.entry("name", "foo"));
	}

	@Test
	void customCleanerIsUsed() {
		MapOutputConverter converter = new MapOutputConverter(text -> "{\"name\":\"replaced\"}");
		assertThat(converter.convert("anything")).containsExactly(Map.entry("name", "replaced"));
	}

}
