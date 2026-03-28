package org.springframework.ai.classification.image;

import org.springframework.ai.model.ModelResult;
import org.springframework.ai.model.ResultMetadata;

import java.util.List;
import java.util.Objects;

/**
 * Represents the classification result of an image processed by a model.
 *
 * <p>
 * Each classification result consists usually of:
 * </p>
 * <ul>
 * <li>A list of {@link ImageClassification} describing the various classifications and
 * respective scores the model predicted</li>
 * <li>{@link ImageClassificationMetaData} arbitrary metadata</li>
 * </ul>
 *
 * @author Denis Lobo
 */
public class ImageClassificationGeneration implements ModelResult<List<ImageClassification>> {

	private final List<ImageClassification> imageClassification;

	private final ImageClassificationMetaData imageClassificationMetaData;

	public ImageClassificationGeneration(List<ImageClassification> imageClassification) {
		this(imageClassification, new ImageClassificationMetaData() {
		});
	}

	public ImageClassificationGeneration(List<ImageClassification> imageClassification,
			ImageClassificationMetaData imageClassificationMetaData) {
		this.imageClassificationMetaData = imageClassificationMetaData;
		this.imageClassification = imageClassification;
	}

	@Override
	public List<ImageClassification> getOutput() {
		return imageClassification;
	}

	@Override
	public ResultMetadata getMetadata() {
		return imageClassificationMetaData;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ImageClassificationGeneration that = (ImageClassificationGeneration) o;
		return Objects.equals(imageClassification, that.imageClassification)
				&& Objects.equals(imageClassificationMetaData, that.imageClassificationMetaData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(imageClassification, imageClassificationMetaData);
	}

	@Override
	public String toString() {
		return "ImageClassificationGeneration{" + "imageClassification=" + imageClassification
				+ ", imageClassificationMetaData=" + imageClassificationMetaData + '}';
	}

}
