package org.springframework.ai.classification.image;

/**
 * Represents a single classification result of an image processed by a model.
 *
 * <p>
 * Each classification consists usually of:
 * </p>
 * <ul>
 * <li>A descriptive label indicating the category or class the image belongs to.</li>
 * <li>A score representing the probability or confidence level for the
 * classification.</li>
 * </ul>
 *
 * @author Denis Lobo
 */
public class ImageClassification {

	private final String label;

	private final double score;

	public ImageClassification(String label, double score) {
		this.label = label;
		this.score = score;
	}

	public String getLabel() {
		return label;
	}

	public double getScore() {
		return score;
	}

	@Override
	public String toString() {
		return "Classification{" + "label='" + label + '\'' + ", score=" + score + '}';
	}

}
