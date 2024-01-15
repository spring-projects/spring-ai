package org.springframework.ai.generative;

public interface GenerativePrompt<T> {

	T getInstructions(); // required input

	Options getOptions();

}