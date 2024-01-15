package org.springframework.ai.image;

import org.springframework.ai.model.ModelResult;

public class ImageGeneration implements ModelResult<Image> {

	private ImageGenerationMetadata imageGenerationMetadata;

	private Image image;

	public ImageGeneration(Image image) {
		this.image = image;
	}

	public ImageGeneration(Image image, ImageGenerationMetadata imageGenerationMetadata) {
		this.image = image;
		this.imageGenerationMetadata = imageGenerationMetadata;
	}

	@Override
	public Image getOutput() {
		return image;
	}

	@Override
	public ImageGenerationMetadata getResultMetadata() {
		return imageGenerationMetadata;
	}

	@Override
	public String toString() {
		return "ImageGeneration{" + "imageGenerationMetadata=" + imageGenerationMetadata + ", image=" + image + '}';
	}

}
