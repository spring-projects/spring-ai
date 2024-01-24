/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.openai.metadata;

import org.springframework.ai.image.ImageGenerationMetadata;

import java.util.Objects;

public class OpenAiImageGenerationMetadata implements ImageGenerationMetadata {

	private String revisedPrompt;

	public OpenAiImageGenerationMetadata(String revisedPrompt) {
		this.revisedPrompt = revisedPrompt;
	}

	public String getRevisedPrompt() {
		return revisedPrompt;
	}

	@Override
	public String toString() {
		return "OpenAiImageGenerationMetadata{" + "revisedPrompt='" + revisedPrompt + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof OpenAiImageGenerationMetadata that))
			return false;
		return Objects.equals(revisedPrompt, that.revisedPrompt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(revisedPrompt);
	}

}
