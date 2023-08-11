package org.springframework.ai.core.chain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface Chain extends Function<Map<String, Object>, Map<String, Object>> {

	List<String> getInputKeys();

	List<String> getOutputKeys();

}
