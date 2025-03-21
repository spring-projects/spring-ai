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
	LLAMA2("llama2", null),

	/**
	 * Llama 3 is a collection of language models ranging from 8B and 70B parameters.
	 */
	LLAMA3("llama3", null),

	/**
	 * The 8B language model from Meta.
	 */
	LLAMA3_1("llama3.1", null),

	/**
	 * The Llama 3.2 3B language model from Meta.
	 */
	LLAMA3_2("llama3.2", null),

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
	LLAMA3_2_1B("llama3.2", "1b"),

	/**
	 * The Llama 3.2 3B language model from Meta.
	 */
	LLAMA3_2_3B("llama3.2:3b"),

	/**
	 * The 7B parameters model
	 */
	MISTRAL("mistral", null),

	/**
	 * A 12B model with 128k context length, built by Mistral AI in collaboration with
	 * NVIDIA.
	 */
	MISTRAL_NEMO("mistral-nemo", null),

	/**
	 * A small vision language model designed to run efficiently on edge devices.
	 */
	MOONDREAM("moondream", null),

	/**
	 * The 2.7B uncensored Dolphin model
	 */
	DOLPHIN_PHI("dolphin-phi", null),

	/**
	 * The Phi-2 2.7B language model
	 */
	PHI("phi", null),

	/**
	 * The Phi-3 3.8B language model
	 */
	PHI3("phi3", null),

	/**
	 * A fine-tuned Mistral model
	 */
	NEURAL_CHAT("neural-chat", null),

	/**
	 * Starling-7B model
	 */
	STARLING_LM("starling-lm", null),

	/**
	 * Code Llama is based on Llama 2 model
	 */
	CODELLAMA("codellama", null),

	/**
	 * Orca Mini is based on Llama and Llama 2 ranging from 3 billion parameters to 70
	 * billion
	 */
	ORCA_MINI("orca-mini", null),

	/**
	 * Llava is a Large Language and Vision Assistant model
	 */
	LLAVA("llava", null),

	/**
	 * Gemma is a lightweight model with 2 billion and 7 billion
	 */
	GEMMA("gemma", null),

	/**
	 * Uncensored Llama 2 model
	 */
	LLAMA2_UNCENSORED("llama2-uncensored", null),

	/**
	 * A high-performing open embedding model with a large token context window.
	 */
	NOMIC_EMBED_TEXT("nomic-embed-text", null),

	/**
	 * State-of-the-art large embedding model from mixedbread.ai
	 */
	MXBAI_EMBED_LARGE("mxbai-embed-large", null),
	/**
	 * Qwen2.5 models are pretrained on Alibaba's latest large-scale dataset, encompassing
	 * up to 18 trillion tokens. The model supports up to 128K tokens and has multilingual
	 * support.
	 */
	QWEN2_5("qwen2.5", null), QWEN2_5_7b("qwen2.5", "7b"), QWEN2_5_14b("qwen2.5", "14b"), QWEN2_5_32b("qwen2.5", "32b"),
	QWEN2_5_72b("qwen2.5", "72b"),
	/**
	 * The latest series of Code-Specific Qwen models, with significant improvements in
	 * code generation, code reasoning, and code fixing.
	 */
	QWEN2_5_CODER("qwen2.5-coder", null), QWEN2_5_CODER_3b("qwen2.5-coder", "3b"),
	QWEN2_5_CODER_7b("qwen2.5-coder", "7b"), QWEN2_5_CODER_14b("qwen2.5-coder", "14b"),
	QWEN2_5_CODER_32b("qwen2.5-coder", "32b"),
	/**
	 * Llama 3.2 Vision is a collection of instruction-tuned image reasoning generative
	 * models in 11B and 90B sizes.
	 */
	LLAMA3_2_VISION("llama3.2-vision", null), LLAMA3_2_VISION_11b("llama3.2-vision", "11b"),
	LLAMA3_2_VISION_90b("llama3.2-vision", "90b");

	/**
	 * model id
	 */
	private final String id;

	/**
	 * model size
	 */
	private final String size;

	OllamaModel(String id, String size) {
		this.id = id;
		this.size = size;
	}

	public String id() {
		return this.id;
	}

	/**
	 * for example: qwen2.5:7b,llama3.2:1b
	 * @return model id with size
	 */
	@Override
	public String getName() {
		return this.id + (this.size == null || this.size.isEmpty() ? "" : ":" + this.size);
	}

	/**
	 * @return model size
	 */
	@Override
	public String getModelSize() {
		return this.size;
	}

}
