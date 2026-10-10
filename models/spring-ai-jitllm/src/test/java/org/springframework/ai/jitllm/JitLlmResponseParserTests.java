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

package org.springframework.ai.jitllm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JitLlmResponseParserTests {

	private final JitLlmResponseParser parser = new JitLlmResponseParser();

	@Test
	void parsesPlainTextAsContent() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("  Hello world.  ");

		assertThat(parsed.content()).isEqualTo("Hello world.");
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void parsesNullAsEmptyContent() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse(null);

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void parsesBlankAsEmptyContent() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse(" \n\t ");

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void extractsSingleThinkBlockBeforeContent() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("""
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
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("A <think>hidden</think> B");

		assertThat(parsed.content()).isEqualTo("A  B");
		assertThat(parsed.thinking()).isEqualTo("hidden");
	}

	@Test
	void extractsMultipleThinkBlocks() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser
			.parse("<think>first</think> Hello <think>second</think> world");

		assertThat(parsed.content()).isEqualTo("Hello  world");
		assertThat(parsed.thinking()).isEqualTo("first\n\nsecond");
	}

	@Test
	void extractsUnclosedThinkTagAsThinking() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("Hello <think>unfinished");

		assertThat(parsed.content()).isEqualTo("Hello");
		assertThat(parsed.thinking()).isEqualTo("unfinished");
	}

	@Test
	void extractsTruncatedThinkingOnlyResponse() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("<think>partial thinking...");

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isEqualTo("partial thinking...");
	}

	@Test
	void extractsPureThinkingResponse() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("<think>x</think>");

		assertThat(parsed.content()).isEmpty();
		assertThat(parsed.thinking()).isEqualTo("x");
	}

	@Test
	void extractsThinkBlockAtEndOfContent() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("Hello <think>x</think>");

		assertThat(parsed.content()).isEqualTo("Hello");
		assertThat(parsed.thinking()).isEqualTo("x");
	}

	@Test
	void keepsClosingTagWithoutOpeningTagInContent() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("Hello </think> world");

		assertThat(parsed.content()).isEqualTo("Hello </think> world");
		assertThat(parsed.thinking()).isNull();
	}

	@Test
	void ignoresEmptyThinkBlock() {
		JitLlmResponseParser.ParsedResponse parsed = this.parser.parse("Hello <think>   </think> world");

		assertThat(parsed.content()).isEqualTo("Hello  world");
		assertThat(parsed.thinking()).isNull();
	}

}
