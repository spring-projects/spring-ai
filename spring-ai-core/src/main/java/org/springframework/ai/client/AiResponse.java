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
package org.springframework.ai.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AiResponse {

	private final List<Generation> generations;

	private Map<String, Object> providerOutput;

	private Map<String, Object> runInfo;

	public AiResponse(List<Generation> generations) {
		this(generations, Collections.emptyMap(), Collections.emptyMap());
	}

	public AiResponse(List<Generation> generations, Map<String, Object> providerOutput) {
		this(generations, providerOutput, Collections.emptyMap());
	}

	public AiResponse(List<Generation> generations, Map<String, Object> providerOutput, Map<String, Object> runInfo) {
		this.generations = List.copyOf(generations);
		this.providerOutput = Map.copyOf(providerOutput);
		this.runInfo = Map.copyOf(runInfo);
	}

	/**
	 * The list of generated outputs. It is a list of lists because the Prompt could
	 * request multiple output generations.
	 * @return
	 */
	public List<Generation> getGenerations() {
		return Collections.unmodifiableList(generations);
	}

	public Generation getGeneration() {
		return this.generations.get(0);
	}

	/**
	 * Arbitrary model provider specific output
	 */
	public Map<String, Object> getProviderOutput() {
		return Collections.unmodifiableMap(providerOutput);
	}

	/**
	 * The run metadata information
	 */
	public Map<String, Object> getRunInfo() {
		return Collections.unmodifiableMap(runInfo);
	}

}
