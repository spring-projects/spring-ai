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
	public AiOutput apply(AiInput aiInput) {
		AiInput aiInputToUse = preProcess(aiInput);
		AiOutput aiOutput = doApply(aiInputToUse);
		Map<String, Object> outputMapToUse = postProcess(aiInput, aiOutput);
		return new AiOutput(outputMapToUse);
	}

	@Override
	public String apply(Map<String, Object> input) {
		AiInput aiInput = new AiInput(input);
		AiOutput aiOutput = apply(aiInput);
		return aiOutput.getOutputData().get(getOutputKeys().get(0)).toString();
	}

	@Override
	public AiInput preProcess(AiInput aiInput) {
		Map<String, Object> inputs = new HashMap<>(aiInput.getInputData());
		if (memory.isPresent()) {
			Map<String, Object> externalContext = memory.get().load(inputs);
			inputs.putAll(externalContext);
		}

		validateInputs(inputs);
		return new AiInput(inputs);
	}

	protected abstract AiOutput doApply(AiInput aiInput);

	@Override
	public Map<String, Object> postProcess(AiInput aiInput, AiOutput aiOutput) {
		validateOutputs(aiOutput.getOutputData());

		if (memory.isPresent()) {
			memory.get().save(aiInput.getInputData(), aiOutput.getOutputData());
		}

		Map<String, Object> combindedMap = new HashMap<>();
		combindedMap.putAll(aiInput.getInputData());
		combindedMap.putAll(aiOutput.getOutputData());
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
