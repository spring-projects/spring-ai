/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.core.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLMResponse {

	private final List<Generation> generations;

	private Map<String, Object> providerOutput = new HashMap<>();

	private Map<String, Object> runInfo = new HashMap<>();

	public LLMResponse(List<Generation> generations) {
		this.generations = generations;
	}

	public LLMResponse(List<Generation> generations, Map<String, Object> providerOutput) {
		this.generations = generations;
		this.providerOutput = providerOutput;
	}

	public LLMResponse(List<Generation> generations, Map<String, Object> providerOutput, Map<String, Object> runInfo) {
		this.generations = generations;
		this.providerOutput = providerOutput;
		this.runInfo = runInfo;
	}

	/**
	 * The list of generated outputs. It is a list of lists because a single input could
	 * have multiple outputs.
	 * @return
	 */
	public List<Generation> getGenerations() {
		return this.generations;
	}

	/**
	 * Arbitrary LLM-provider specific output
	 */
	public Map<String, Object> getProviderOutput() {
		return null;
	}

	/**
	 * The run metadata information
	 */
	public Map<String, Object> getRunInfo() {
		return null;
	}

}
