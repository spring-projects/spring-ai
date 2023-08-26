package org.springframework.ai.parser;

@FunctionalInterface
public interface Parser<T> {

	T parse(String text);

}
