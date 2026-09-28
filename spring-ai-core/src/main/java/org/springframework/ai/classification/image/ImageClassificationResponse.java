package org.springframework.ai.classification.image;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.model.ResponseMetadata;

import java.util.List;

/**
 * The image classification response returned by an image classification model.
 *
 * <p>
 * Each response consists of:
 * </p>
 * <ul>
 * <li>{@link ImageClassificationGeneration} holding the results of an classified
 * image</li>
 * <li>{@link ImageClassificationResponseMetadata} holding metadata provided by the image
 * classifying model</li>
 * </ul>
 *
 * @author Denis Lobo
 */
public class ImageClassificationResponse implements ModelResponse<ImageClassificationGeneration> {

	private final ImageClassificationGeneration imageClassification;

	private final ImageClassificationResponseMetadata metadata;

	public ImageClassificationResponse(ImageClassificationGeneration imageClassification,
			ImageClassificationResponseMetadata metadata) {
		this.imageClassification = imageClassification;
		this.metadata = metadata;
	}

	@Override
	public ImageClassificationGeneration getResult() {
		return imageClassification;
	}

	@Override
	public List<ImageClassificationGeneration> getResults() {
		return List.of(imageClassification);
	}

	@Override
	public ResponseMetadata getMetadata() {
		return metadata;
	}

	@Override
	public String toString() {
		return "ImageClassificationResponse{" + "imageClassification=" + imageClassification + ", metadata=" + metadata
				+ '}';
	}

}
