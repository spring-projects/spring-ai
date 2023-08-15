package org.springframework.ai.memory;

import java.util.List;
import java.util.Map;

public interface Memory {

	/**
	 * The keys that the memory will add to Chain inputs
	 */
	List<String> getKeys();

	/**
	 * Return key-value pairs given the text input to the chain
	 * @param inputs input of the chain
	 * @return key-value pairs from memory
	 */
	Map<String, Object> load(Map<String, Object> inputs);

	void save(Map<String, Object> inputs, Map<String, Object> outputs);

}
