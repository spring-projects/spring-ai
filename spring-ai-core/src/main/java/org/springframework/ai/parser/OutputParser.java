package org.springframework.ai.parser;

import org.springframework.ai.client.Generation;

import java.util.List;

public interface OutputParser<T> {

	T parse(List<Generation> output);

}
