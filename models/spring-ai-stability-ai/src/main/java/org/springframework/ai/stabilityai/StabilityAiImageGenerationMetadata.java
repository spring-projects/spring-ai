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

package org.springframework.ai.stabilityai;

import java.util.Objects;

import org.springframework.ai.image.ImageGenerationMetadata;

/**
 * Represents metadata associated with the image generation process in the StabilityAI
 * framework.
 */
public class StabilityAiImageGenerationMetadata implements ImageGenerationMetadata {

	private final String finishReason;

	private final Long seed;

	public StabilityAiImageGenerationMetadata(String finishReason, Long seed) {
		this.finishReason = finishReason;
		this.seed = seed;
	}

	public String getFinishReason() {
		return this.finishReason;
	}

	public Long getSeed() {
		return this.seed;
	}

	@Override
	public String toString() {
		return "StabilityAiImageGenerationMetadata{" + "finishReason='" + this.finishReason + '\'' + ", seed="
				+ this.seed + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StabilityAiImageGenerationMetadata that)) {
			return false;
		}
		return Objects.equals(this.finishReason, that.finishReason) && Objects.equals(this.seed, that.seed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.finishReason, this.seed);
	}

}
