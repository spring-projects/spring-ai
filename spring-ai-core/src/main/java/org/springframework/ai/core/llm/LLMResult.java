package org.springframework.ai.core.llm;/*
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

import java.util.List;
import java.util.Map;

import org.springframework.ai.core.prompt.Generation;

public interface LLMResult {

	/**
	 * The list of generated outputs. It iss a list of lists because a single input could
	 * have multiple outputs, and multiple inputs could be passed in.
	 * @return
	 */
	List<List<Generation>> getGenerations();

	/**
	 * Arbitrary LLM-provider specific output
	 */
	Map<String, Object> getProviderOutput();

	/**
	 * The run metadata information
	 */
	Map<String, Object> getRunInfo();

}
