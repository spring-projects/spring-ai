package org.springframework.ai.parser;

import java.util.Locale;

@FunctionalInterface
public interface Parser<T> {

	T parse(String text);

}
