package org.springframework.ai.core.parser;

import org.springframework.ai.core.llm.Generation;

import java.util.List;

public interface OutputParser<T> {

	T parse(List<Generation> output);

}
