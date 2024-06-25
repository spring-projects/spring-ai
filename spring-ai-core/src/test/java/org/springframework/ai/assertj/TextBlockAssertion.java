package org.springframework.ai.assertj;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class TextBlockAssertion extends AbstractAssert<TextBlockAssertion, String> {

	protected TextBlockAssertion(String string) {
		super(string, TextBlockAssertion.class);
	}

	public static TextBlockAssertion assertThat(String actual) {
		return new TextBlockAssertion(actual);
	}

	@Override
	public TextBlockAssertion isEqualTo(Object expected) {
		Assertions.assertThat(actual.replaceAll("\r\n", "\n")).isEqualTo(expected);
		return this;
	}

}