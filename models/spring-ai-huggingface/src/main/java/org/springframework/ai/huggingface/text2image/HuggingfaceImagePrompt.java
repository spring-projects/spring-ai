package org.springframework.ai.huggingface.text2image;

import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;

import java.util.Collections;
import java.util.List;

public class HuggingfaceImagePrompt extends ImagePrompt {

	public HuggingfaceImagePrompt(List<ImageMessage> messages) {
		this(messages, null);
	}

	public HuggingfaceImagePrompt(List<ImageMessage> messages, HuggingfaceImageOptions imageModelOptions) {
		super(messages, imageModelOptions);
	}

	public HuggingfaceImagePrompt(ImageMessage imageMessage, HuggingfaceImageOptions imageOptions) {
		this(Collections.singletonList(imageMessage), imageOptions);
	}

	public HuggingfaceImagePrompt(String instructions, HuggingfaceImageOptions imageOptions) {
		this(new ImageMessage(instructions), imageOptions);
	}

	public HuggingfaceImagePrompt(String instructions) {
		this(new ImageMessage(instructions), new HuggingfaceImageOptions());
	}

}
