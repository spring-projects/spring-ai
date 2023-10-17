package org.springframework.ai.reader;

import java.util.Collections;
import java.util.Map;

public class EmptyJsonMetadataGenerator implements JsonMetadataGenerator {

	private static final Map<String, Object> EMPTY_MAP = Collections.emptyMap();

	@Override
	public Map<String, Object> generate(Map<String, Object> jsonMap) {
		return EMPTY_MAP;
	}

}
