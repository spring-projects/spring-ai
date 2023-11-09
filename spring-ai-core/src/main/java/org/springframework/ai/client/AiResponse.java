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

import org.springframework.ai.metadata.GenerationMetadata;
import org.springframework.ai.metadata.PromptMetadata;
import org.springframework.lang.Nullable;

public class AiResponse {

	private final GenerationMetadata metadata;

	private final List<Generation> generations;

	private PromptMetadata promptMetadata;

	public AiResponse(List<Generation> generations) {
		this(generations, GenerationMetadata.NULL);
	}

	public AiResponse(List<Generation> generations, GenerationMetadata metadata) {
		this.metadata = metadata;
		this.generations = List.copyOf(generations);
	}

	/**
	 * The {@link List} of {@link Generation generated outputs}.
	 * <p>
	 * It is a {@link List} of {@link List lists} because the Prompt could request
	 * multiple output {@link Generation generations}.
	 * @return the {@link List} of {@link Generation generated outputs}.
	 */
	public List<Generation> getGenerations() {
		return this.generations;
	}

	public Generation getGeneration() {
		return this.generations.get(0);
	}

	/**
	 * Returns {@link GenerationMetadata} containing information about the use of the AI
	 * provider's API.
	 * @return {@link GenerationMetadata} containing information about the use of the AI
	 * provider's API.
	 */
	public GenerationMetadata getGenerationMetadata() {
		return this.metadata;
	}

	/**
	 * Returns {@link PromptMetadata} containing information on prompt processing by the
	 * AI.
	 * @return {@link PromptMetadata} containing information on prompt processing by the
	 * AI.
	 */
	public PromptMetadata getPromptMetadata() {
		PromptMetadata promptMetadata = this.promptMetadata;
		return promptMetadata != null ? promptMetadata : PromptMetadata.empty();
	}

	/**
	 * Builder method used to include {@link PromptMetadata} returned in the AI response
	 * when processing the prompt.
	 * @param promptMetadata {@link PromptMetadata} returned by the AI in the response
	 * when processing the prompt.
	 * @return this {@link AiResponse}.
	 * @see #getPromptMetadata()
	 */
	public AiResponse withPromptMetadata(@Nullable PromptMetadata promptMetadata) {
		this.promptMetadata = promptMetadata;
		return this;
	}

}
