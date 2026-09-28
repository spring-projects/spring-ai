package org.springframework.ai.classification.image;

import org.springframework.ai.model.ModelRequest;

/**
 * Represents a request for image classification models.
 *
 * <p>
 * Each prompt consists of:
 * </p>
 * <ul>
 * <li>{@link ImageClassificationMessage} (e.g. binary or base64 encoded image</li>
 * <li>{@link ImageClassificationOptions} (e.g. configuration options (e.g. top_k,
 * function_to_apply on scores</li>
 * </ul>
 *
 * @author Denis Lobo
 */
public class ImageClassificationPrompt implements ModelRequest<ImageClassificationMessage> {

	private final ImageClassificationMessage message;

	private final ImageClassificationOptions options;

	public ImageClassificationPrompt(ImageClassificationMessage message, ImageClassificationOptions options) {
		this.message = message;
		this.options = options;
	}

	@Override
	public ImageClassificationMessage getInstructions() {
		return message;
	}

	@Override
	public ImageClassificationOptions getOptions() {
		return options;
	}

}
