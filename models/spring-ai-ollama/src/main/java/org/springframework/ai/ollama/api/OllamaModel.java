/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.ollama.api;

import org.springframework.ai.model.ChatModelDescription;

/**
 * Helper class for common Ollama models.
 *
 * @author Siarhei Blashuk
 * @since 0.8.1
 */
public enum OllamaModel implements ChatModelDescription {

	/**
	 * Llama 2 is a collection of language models ranging from 7B to 70B parameters.
	 */
	LLAMA2("llama2"),

	/**
	 * Llama 3 is a collection of language models ranging from 8B and 70B parameters.
	 */
	LLAMA3("llama3"),

	/**
	 * The 7B parameters model
	 */
	MISTRAL("mistral"),

	/**
	 * The 2.7B uncensored Dolphin model
	 */
	DOLPHIN_PHI("dolphin-phi"),

	/**
	 * The Phi-2 2.7B language model
	 */
	PHI("phi"),

	/**
	 * The Phi-3 3.8B language model
	 */
	PHI3("phi3"),

	/**
	 * A fine-tuned Mistral model
	 */
	NEURAL_CHAT("neural-chat"),

	/**
	 * Starling-7B model
	 */
	STARLING_LM("starling-lm"),

	/**
	 * Code Llama is based on Llama 2 model
	 */
	CODELLAMA("codellama"),

	/**
	 * Orca Mini is based on Llama and Llama 2 ranging from 3 billion parameters to 70
	 * billion
	 */
	ORCA_MINI("orca-mini"),

	/**
	 * Llava is a Large Language and Vision Assistant model
	 */
	LLAVA("llava"),

	/**
	 * Gemma is a lightweight model with 2 billion and 7 billion
	 */
	GEMMA("gemma"),

	/**
	 * Uncensored Llama 2 model
	 */
	LLAMA2_UNCENSORED("llama2-uncensored");

	private final String id;

	OllamaModel(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	@Override
	public String getName() {
		return this.id;
	}

}
