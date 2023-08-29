package org.springframework.ai.chain;

import java.util.Map;

public class AiInput {

	private Map<String, Object> inputData;

	public AiInput(Map<String, Object> inputData) {
		this.inputData = inputData;
	}

	Map<String, Object> getInputData() {
		return inputData;
	}

}
