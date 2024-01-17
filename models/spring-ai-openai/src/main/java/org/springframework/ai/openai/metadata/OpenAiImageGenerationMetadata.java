package org.springframework.ai.openai.metadata;

import org.springframework.ai.image.ImageGenerationMetadata;

import java.util.Objects;

public class OpenAiImageGenerationMetadata implements ImageGenerationMetadata {

	private String revisedPrompt;

	public OpenAiImageGenerationMetadata(String revisedPrompt) {
		this.revisedPrompt = revisedPrompt;
	}

	@Override
	public String revisedPrompt() {
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
