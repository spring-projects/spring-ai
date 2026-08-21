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

package org.springframework.ai.wavespeed;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.wavespeed.api.WaveSpeedApi;
import org.springframework.ai.wavespeed.api.WaveSpeedImageOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link ImageModel} implementation backed by the WaveSpeed AI image generation API.
 *
 * <p>
 * The WaveSpeed API is asynchronous, so a call submits a prediction and polls until it
 * completes, then returns the generated images as URLs.
 *
 * @author Zeyi Cheng
 * @since 2.0.1
 */
public class WaveSpeedImageModel implements ImageModel {

	private final WaveSpeedApi waveSpeedApi;

	private final WaveSpeedImageOptions options;

	public WaveSpeedImageModel(WaveSpeedApi waveSpeedApi) {
		this(waveSpeedApi, WaveSpeedImageOptions.builder().build());
	}

	public WaveSpeedImageModel(WaveSpeedApi waveSpeedApi, WaveSpeedImageOptions options) {
		Assert.notNull(waveSpeedApi, "'waveSpeedApi' must not be null");
		Assert.notNull(options, "'options' must not be null");
		this.waveSpeedApi = waveSpeedApi;
		this.options = options;
	}

	public WaveSpeedImageOptions getOptions() {
		return this.options;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		Assert.notNull(imagePrompt, "'imagePrompt' must not be null");
		Assert.notEmpty(imagePrompt.getInstructions(), "'imagePrompt' must contain at least one message");

		WaveSpeedImageOptions requestOptions = mergeOptions(imagePrompt.getOptions(), this.options);

		String model = (requestOptions.getModel() != null) ? requestOptions.getModel()
				: WaveSpeedApi.DEFAULT_IMAGE_MODEL;

		String prompt = imagePrompt.getInstructions()
			.stream()
			.map(ImageMessage::getText)
			.reduce((first, second) -> first + " " + second)
			.orElseThrow();

		WaveSpeedApi.GenerateImageRequest request = new WaveSpeedApi.GenerateImageRequest(prompt,
				requestOptions.toSizeString(), requestOptions.getSeed());

		WaveSpeedApi.Prediction prediction = this.waveSpeedApi.generateImage(model, request);

		return convertResponse(prediction);
	}

	private ImageResponse convertResponse(WaveSpeedApi.Prediction prediction) {
		List<String> outputs = prediction.outputs();
		if (CollectionUtils.isEmpty(outputs)) {
			return new ImageResponse(List.of(), new ImageResponseMetadata());
		}
		List<ImageGeneration> imageGenerations = outputs.stream()
			.map(url -> new ImageGeneration(new Image(url, null)))
			.toList();
		return new ImageResponse(imageGenerations, new ImageResponseMetadata());
	}

	/**
	 * Merge the runtime options passed via the prompt with the default options configured
	 * via the constructor. Runtime options overwrite the default options.
	 */
	WaveSpeedImageOptions mergeOptions(@Nullable ImageOptions runtimeOptions, WaveSpeedImageOptions defaultOptions) {
		if (runtimeOptions == null) {
			return defaultOptions;
		}
		WaveSpeedImageOptions.Builder builder = WaveSpeedImageOptions.builder()
			// Handle portable image options
			.model(ModelOptionsUtils.mergeOption(runtimeOptions.getModel(), defaultOptions.getModel()))
			.width(ModelOptionsUtils.mergeOption(runtimeOptions.getWidth(), defaultOptions.getWidth()))
			.height(ModelOptionsUtils.mergeOption(runtimeOptions.getHeight(), defaultOptions.getHeight()))
			// Always set the WaveSpeed-specific defaults
			.size(defaultOptions.getSize())
			.seed(defaultOptions.getSeed());
		if (runtimeOptions instanceof WaveSpeedImageOptions waveSpeedOptions) {
			// Handle WaveSpeed AI specific image options
			builder.size(ModelOptionsUtils.mergeOption(waveSpeedOptions.getSize(), defaultOptions.getSize()))
				.seed(ModelOptionsUtils.mergeOption(waveSpeedOptions.getSeed(), defaultOptions.getSeed()));
		}
		return builder.build();
	}

}
