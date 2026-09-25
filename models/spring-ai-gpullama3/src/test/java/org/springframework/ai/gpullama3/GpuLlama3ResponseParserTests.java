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

package org.springframework.ai.gpullama3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GpuLlama3ResponseParserTests {

	private final GpuLlama3ResponseParser parser = new GpuLlama3ResponseParser();

	@Test
	void parsesPlainTextAsContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("  Hello world.  ");

		assertThat(parsed.content()).isEqualTo("Hello world.");
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void parsesNullAsEmptyContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse(null);

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void parsesBlankAsEmptyContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse(" \n\t ");

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void extractsSingleThinkBlockBeforeContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("""
				<think>
				I should answer shortly.
				</think>
				Hello.
				""");

		assertThat(parsed.content()).isEqualTo("Hello.");
		assertThat(parsed.thinking()).isEqualTo("I should answer shortly.");
	}

	@Test
	void extractsThinkBlockInMiddleOfContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("A <think>hidden</think> B");

		assertThat(parsed.content()).isEqualTo("A  B");
		assertThat(parsed.thinking()).isEqualTo("hidden");
	}

	@Test
	void extractsMultipleThinkBlocks() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser
			.parse("<think>first</think> Hello <think>second</think> world");

		assertThat(parsed.content()).isEqualTo("Hello  world");
		assertThat(parsed.thinking()).isEqualTo("first\n\nsecond");
	}

	@Test
	void extractsUnclosedThinkTagAsThinking() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("Hello <think>unfinished");

		assertThat(parsed.content()).isEqualTo("Hello");
		assertThat(parsed.thinking()).isEqualTo("unfinished");
	}

	@Test
	void extractsTruncatedThinkingOnlyResponse() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("<think>partial thinking...");

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isEqualTo("partial thinking...");
	}

	@Test
	void extractsPureThinkingResponse() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("<think>x</think>");

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isEqualTo("x");
	}

	@Test
	void extractsThinkBlockAtEndOfContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("Hello <think>x</think>");

		assertThat(parsed.content()).isEqualTo("Hello");
		assertThat(parsed.thinking()).isEqualTo("x");
	}

	@Test
	void keepsClosingTagWithoutOpeningTagInContent() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("Hello </think> world");

		assertThat(parsed.content()).isEqualTo("Hello </think> world");
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void ignoresEmptyThinkBlock() {
		GpuLlama3ResponseParser.ParsedResponse parsed = this.parser.parse("Hello <think>   </think> world");

		assertThat(parsed.content()).isEqualTo("Hello  world");
		assertThat(parsed.thinking()).isNull();
	}

}
