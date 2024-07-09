package org.springframework.ai.assertj;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.assertj.core.api.Assertions;

import java.util.Arrays;

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