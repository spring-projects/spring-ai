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

package org.springframework.ai.chat.model;

import java.util.Objects;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.GenerationMetadata;
import org.springframework.ai.model.ModelResult;

/**
 * Represents a response returned by the AI.
 */
public class Generation implements ModelResult<AssistantMessage> {

	private final AssistantMessage assistantMessage;

	private GenerationMetadata generationMetadata;

	public Generation(AssistantMessage assistantMessage) {
		this(assistantMessage, GenerationMetadata.NULL);
	}

	public Generation(AssistantMessage assistantMessage, GenerationMetadata generationMetadata) {
		this.assistantMessage = assistantMessage;
		this.generationMetadata = generationMetadata;
	}

	@Override
	public AssistantMessage getOutput() {
		return this.assistantMessage;
	}

	@Override
	public GenerationMetadata getMetadata() {
		GenerationMetadata generationMetadata = this.generationMetadata;
		return generationMetadata != null ? generationMetadata : GenerationMetadata.NULL;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Generation that)) {
			return false;
		}
		return Objects.equals(this.assistantMessage, that.assistantMessage)
				&& Objects.equals(this.generationMetadata, that.generationMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.assistantMessage, this.generationMetadata);
	}

	@Override
	public String toString() {
		return "Generation[" + "assistantMessage=" + this.assistantMessage + ", generationMetadata="
				+ this.generationMetadata + ']';
	}

}
