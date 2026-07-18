/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model.togetherai.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.togetherai.api.TogetherAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Together AI image properties.
 *
 * @since 2.0.1
 * @author Maksym Uimanov
 */
@ConfigurationProperties(TogetherAiImageProperties.CONFIG_PREFIX)
public class TogetherAiImageProperties extends TogetherAiParentProperties {
	public static final String CONFIG_PREFIX = "spring.ai.togetherai.image";
	@Nullable
	private String model;
	@Nullable
	private Integer steps;
	@Nullable
	private String imageUrl;
	@Nullable
	private Long seed;
	@Nullable
	private Integer n;
	@Nullable
	private Integer height;
	@Nullable
	private Integer width;
	@Nullable
	private String negativePrompt;
	@Nullable
	private String responseFormat;
	@Nullable
	private Float guidanceScale;
	@Nullable
	private String outputFormat;
	@Nullable
	private List<TogetherAiImageOptions.ImageLora> imageLoras;
	@Nullable
	private List<String> referenceImages;
	@Nullable
	private Boolean disableSafetyChecker;

	@Nullable
	public String getModel() {
		return model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	@Nullable
	public Integer getSteps() {
		return steps;
	}

	public void setSteps(@Nullable Integer steps) {
		this.steps = steps;
	}

	@Nullable
	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(@Nullable String imageUrl) {
		this.imageUrl = imageUrl;
	}

	@Nullable
	public Long getSeed() {
		return seed;
	}

	public void setSeed(@Nullable Long seed) {
		this.seed = seed;
	}

	@Nullable
	public Integer getN() {
		return n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	@Nullable
	public Integer getHeight() {
		return height;
	}

	public void setHeight(@Nullable Integer height) {
		this.height = height;
	}

	@Nullable
	public Integer getWidth() {
		return width;
	}

	public void setWidth(@Nullable Integer width) {
		this.width = width;
	}

	@Nullable
	public String getNegativePrompt() {
		return negativePrompt;
	}

	public void setNegativePrompt(@Nullable String negativePrompt) {
		this.negativePrompt = negativePrompt;
	}

	@Nullable
	public String getResponseFormat() {
		return responseFormat;
	}

	public void setResponseFormat(@Nullable String responseFormat) {
		this.responseFormat = responseFormat;
	}

	@Nullable
	public Float getGuidanceScale() {
		return guidanceScale;
	}

	public void setGuidanceScale(@Nullable Float guidanceScale) {
		this.guidanceScale = guidanceScale;
	}

	@Nullable
	public String getOutputFormat() {
		return outputFormat;
	}

	public void setOutputFormat(@Nullable String outputFormat) {
		this.outputFormat = outputFormat;
	}

	@Nullable
	public List<TogetherAiImageOptions.ImageLora> getImageLoras() {
		return imageLoras;
	}

	public void setImageLoras(@Nullable List<TogetherAiImageOptions.ImageLora> imageLoras) {
		this.imageLoras = imageLoras;
	}

	@Nullable
	public List<String> getReferenceImages() {
		return referenceImages;
	}

	public void setReferenceImages(@Nullable List<String> referenceImages) {
		this.referenceImages = referenceImages;
	}

	@Nullable
	public Boolean getDisableSafetyChecker() {
		return disableSafetyChecker;
	}

	public void setDisableSafetyChecker(@Nullable Boolean disableSafetyChecker) {
		this.disableSafetyChecker = disableSafetyChecker;
	}

	public TogetherAiImageOptions toOptions() {
		return TogetherAiImageOptions.builder()
				.model(this.model)
				.steps(this.steps)
				.imageUrl(this.imageUrl)
				.seed(this.seed)
				.n(this.n)
				.height(this.height)
				.width(this.width)
				.negativePrompt(this.negativePrompt)
				.responseFormat(TogetherAiImageOptions.ResponseFormat.from(this.responseFormat))
				.guidanceScale(this.guidanceScale)
				.outputFormat(TogetherAiImageOptions.OutputFormat.from(this.outputFormat))
				.imageLoras(this.imageLoras)
				.referenceImages(this.referenceImages)
				.disableSafetyChecker(this.disableSafetyChecker)
				.build();
	}
}
