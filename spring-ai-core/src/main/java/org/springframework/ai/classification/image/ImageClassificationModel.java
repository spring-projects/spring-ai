package org.springframework.ai.classification.image;

import org.springframework.ai.model.Model;

/**
 * Interface to invoke image classification models
 *
 * @author Denis Lobo
 */
@FunctionalInterface
public interface ImageClassificationModel extends Model<ImageClassificationPrompt, ImageClassificationResponse> {

	ImageClassificationResponse call(ImageClassificationPrompt request);

}
