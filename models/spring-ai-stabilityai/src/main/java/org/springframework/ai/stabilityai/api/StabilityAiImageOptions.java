package org.springframework.ai.stabilityai.api;

import org.springframework.ai.image.ImageOptions;

public interface StabilityAiImageOptions extends ImageOptions {

	Float getCfgScale();

	String getClipGuidancePreset();

	String getSampler();

	Integer getSamples();

	Long getSeed();

	Integer getSteps();

	String getStylePreset();

	// extras json object...

}
