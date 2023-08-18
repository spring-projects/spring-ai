package org.springframework.ai.chain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface Chain extends Function<AiInput, AiOutput> {

	List<String> getInputKeys();

	List<String> getOutputKeys();

	AiInput preProcess(AiInput aiInput);

	Map<String, Object> postProcess(AiInput aiInput, AiOutput aiOutput);

}
