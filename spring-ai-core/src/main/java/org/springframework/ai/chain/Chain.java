package org.springframework.ai.chain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface Chain extends Function<Map<String, Object>, Map<String, Object>> {

	List<String> getInputKeys();

	List<String> getOutputKeys();

	Map<String, Object> preProcess(Map<String, Object> inputMap);

	Map<String, Object> postProcess(Map<String, Object> inputMap, Map<String, Object> outputMap);

}
