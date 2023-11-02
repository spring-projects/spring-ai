package org.springframework.ai.memory;

import java.util.List;
import java.util.Map;

/**
 * Base interface for memory in chains.
 *
 * @author Mark Pollack
 * @author Craig Walls
 */
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

	/**
	 * Save context of the chain to memory.
	 * @param inputs input of the chain; typically the human input
	 * @param outputs output of the chain; typically the AI response
	 */
	void save(Map<String, Object> inputs, Map<String, Object> outputs);

	/**
	 * Clear the memory.
	 */
	void clear();

}
