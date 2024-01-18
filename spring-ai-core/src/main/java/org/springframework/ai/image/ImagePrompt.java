package org.springframework.ai.image;

import org.springframework.ai.model.ModelRequest;

import java.util.Objects;

public class ImagePrompt implements ModelRequest<String> {

	private String prompt;

	private ImageOptions imageModelOptions;

	public ImagePrompt(String prompt) {
		this.prompt = prompt;
	}

	public ImagePrompt(String prompt, ImageOptions imageModelOptions) {
		this.prompt = prompt;
		this.imageModelOptions = imageModelOptions;
	}

	@Override
	public String getInstructions() {
		return prompt;
	}

	@Override
	public ImageOptions getOptions() {
		return imageModelOptions;
	}

	@Override
	public String toString() {
		return "ImagePrompt{" + "prompt='" + prompt + '\'' + ", imageModelOptions=" + imageModelOptions + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ImagePrompt that))
			return false;
		return Objects.equals(prompt, that.prompt) && Objects.equals(imageModelOptions, that.imageModelOptions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, imageModelOptions);
	}

}
