package org.springframework.ai.reader;

import java.util.Map;

@FunctionalInterface
public interface JsonMetadataGenerator {

	/**
	 * The input is the JSON document represented as a map, the output are the fields
	 * extracted from the input map that will be used as metadata.
	 */
	Map<String, Object> generate(Map<String, Object> jsonMap);

}
