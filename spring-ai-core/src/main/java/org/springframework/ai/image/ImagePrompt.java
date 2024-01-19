package org.springframework.ai.image;

import org.springframework.ai.model.ModelRequest;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ImagePrompt implements ModelRequest<List<ImageMessage>> {

	private final List<ImageMessage> messages;

	private ImageOptions imageModelOptions;

	public ImagePrompt(List<ImageMessage> messages) {
		this.messages = messages;
	}

	public ImagePrompt(List<ImageMessage> messages, ImageOptions imageModelOptions) {
		this.messages = messages;
		this.imageModelOptions = imageModelOptions;
	}

	public ImagePrompt(ImageMessage imageMessage, ImageOptions imageOptions) {
		this(Collections.singletonList(imageMessage), imageOptions);
	}

	public ImagePrompt(String instructions, ImageOptions imageOptions) {
		this(new ImageMessage(instructions), imageOptions);
	}

	public ImagePrompt(String instructions) {
		this(new ImageMessage(instructions), ImageOptionsBuilder.builder().build());
	}

	@Override
	public List<ImageMessage> getInstructions() {
		return messages;
	}

	@Override
	public ImageOptions getOptions() {
		return imageModelOptions;
	}

	@Override
	public String toString() {
		return "NewImagePrompt{" + "messages=" + messages + ", imageModelOptions=" + imageModelOptions + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ImagePrompt that))
			return false;
		return Objects.equals(messages, that.messages) && Objects.equals(imageModelOptions, that.imageModelOptions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(messages, imageModelOptions);
	}

}
