package org.springframework.ai.chain;

import java.util.Map;

public class AiOutput {

	private final Map<String, Object> outputData;

	public AiOutput(Map<String, Object> outputData) {
		this.outputData = outputData;
	}

	Map<String, Object> getOutputData() {
		return this.outputData;
	}

}
