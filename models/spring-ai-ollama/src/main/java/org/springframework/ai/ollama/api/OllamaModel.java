/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.ollama.api;

import org.springframework.ai.model.ChatModelDescription;

/**
 * Helper class for common Ollama models.
 *
 * @author Siarhei Blashuk
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum OllamaModel implements ChatModelDescription {

	/**
	 * Qwen 2.5
	 */
	QWEN_2_5_7B("qwen2.5"),

	/**
	 * Llama 2 is a collection of language models ranging from 7B to 70B parameters.
	 */
	LLAMA2("llama2"),

	/**
	 * Llama 3 is a collection of language models ranging from 8B and 70B parameters.
	 */
	LLAMA3("llama3"),

	/**
	 * The 8B language model from Meta.
	 */
	LLAMA3_1("llama3.1"),

	/**
	 * The Llama 3.2 3B language model from Meta.
	 */
	LLAMA3_2("llama3.2"),

	/**
	 * The Llama 3.2 Vision 11B language model from Meta.
	 */
	LLAMA3_2_VISION_11b("llama3.2-vision"),

	/**
	 * The Llama 3.2 Vision 90B language model from Meta.
	 */
	LLAMA3_2_VISION_90b("llama3.2-vision:90b"),

	/**
	 * The Llama 3.2 1B language model from Meta.
	 */
	LLAMA3_2_1B("llama3.2:1b"),

	/**
	 * The Llama 3.2 3B language model from Meta.
	 */
	LLAMA3_2_3B("llama3.2:3b"),

	/**
	 * The 7B parameters model
	 */
	MISTRAL("mistral"),

	/**
	 * A 12B model with 128k context length, built by Mistral AI in collaboration with
	 * NVIDIA.
	 */
	MISTRAL_NEMO("mistral-nemo"),

	/**
	 * A small vision language model designed to run efficiently on edge devices.
	 */
	MOONDREAM("moondream"),

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
	LLAMA2_UNCENSORED("llama2-uncensored"),

	/**
	 * A high-performing open embedding model with a large token context window.
	 */
	NOMIC_EMBED_TEXT("nomic-embed-text"),

	/**
	 * State-of-the-art large embedding model from mixedbread.ai
	 */
	MXBAI_EMBED_LARGE("mxbai-embed-large");

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
