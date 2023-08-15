package org.springframework.ai.chain;

import org.springframework.ai.memory.Memory;

import java.util.*;

public abstract class AbstractChain implements Chain {

	private Optional<Memory> memory = Optional.empty();

	public Optional<Memory> getMemory() {
		return this.memory;
	}

	public void setMemory(Memory memory) {
		Objects.requireNonNull(memory, "Memory can not be null.");
		this.memory = Optional.of(memory);
	}

	@Override
	public abstract List<String> getInputKeys();

	@Override
	public abstract List<String> getOutputKeys();

	// TODO validation of input/outputs

	@Override
	public Map<String, Object> apply(Map<String, Object> inputMap) {
		Map<String, Object> inputMapToUse = preProcess(inputMap);
		Map<String, Object> outputMap = doApply(inputMapToUse);
		Map<String, Object> outputMapToUse = postProcess(inputMapToUse, outputMap);
		return outputMapToUse;
	}

	@Override
	public Map<String, Object> preProcess(Map<String, Object> inputMap) {
		validateInputs(inputMap);
		return inputMap;
	}

	protected abstract Map<String, Object> doApply(Map<String, Object> inputMap);

	@Override
	public Map<String, Object> postProcess(Map<String, Object> inputMap, Map<String, Object> outputMap) {
		validateOutputs(outputMap);
		Map<String, Object> combindedMap = new HashMap<>();
		combindedMap.putAll(inputMap);
		combindedMap.putAll(outputMap);
		return combindedMap;
	}

	protected void validateOutputs(Map<String, Object> outputMap) {
		Set<String> missingKeys = new HashSet<>(getOutputKeys());
		missingKeys.removeAll(outputMap.keySet());
		if (!missingKeys.isEmpty()) {
			throw new IllegalArgumentException("Missing some output keys: " + missingKeys);
		}
	}

	protected void validateInputs(Map<String, Object> inputMap) {
		Set<String> missingKeys = new HashSet<>(getInputKeys());
		missingKeys.removeAll(inputMap.keySet());
		if (!missingKeys.isEmpty()) {
			throw new IllegalArgumentException("Missing some input keys: " + missingKeys);
		}
	}

}
