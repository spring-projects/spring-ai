/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.document;

import java.util.Arrays;

import org.assertj.core.api.AbstractCharSequenceAssert;
import org.assertj.core.api.Assertions;

public class TextBlockAssertion extends AbstractCharSequenceAssert<TextBlockAssertion, String> {

	protected TextBlockAssertion(String string) {
		super(string, TextBlockAssertion.class);
	}

	public static TextBlockAssertion assertThat(String actual) {
		return new TextBlockAssertion(actual);
	}

	@Override
	public TextBlockAssertion isEqualTo(Object expected) {
		Assertions.assertThat(normalizedEOL(actual)).isEqualTo(normalizedEOL((String) expected));
		return this;
	}

	@Override
	public TextBlockAssertion contains(CharSequence... values) {
		Assertions.assertThat(normalizedEOL(actual)).contains(normalizedEOL(values));
		return this;
	}

	private String normalizedEOL(CharSequence... values) {
		return Arrays.stream(values).map(CharSequence::toString).map(this::normalizedEOL).reduce("", (a, b) -> a + b);
	}

	private String normalizedEOL(String line) {
		return line.replaceAll("\r\n|\r|\n", System.lineSeparator());
	}

}
