package org.springframework.ai.stabilityai;

import org.springframework.ai.image.ImageGenerationMetadata;

import java.util.Objects;

public class StabilityAiImageGenerationMetadata implements ImageGenerationMetadata {

	private String finishReason;

	private Long seed;

	public StabilityAiImageGenerationMetadata(String finishReason, Long seed) {
		this.finishReason = finishReason;
		this.seed = seed;
	}

	public String getFinishReason() {
		return finishReason;
	}

	public Long getSeed() {
		return seed;
	}

	@Override
	public String toString() {
		return "StabilityAiImageGenerationMetadata{" + "finishReason='" + finishReason + '\'' + ", seed=" + seed + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof StabilityAiImageGenerationMetadata that))
			return false;
		return Objects.equals(finishReason, that.finishReason) && Objects.equals(seed, that.seed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(finishReason, seed);
	}

}
