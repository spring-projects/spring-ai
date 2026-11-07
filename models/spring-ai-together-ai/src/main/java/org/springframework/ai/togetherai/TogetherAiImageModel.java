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

package org.springframework.ai.togetherai;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
import org.springframework.ai.togetherai.api.TogetherAiApi;
import org.springframework.ai.togetherai.api.TogetherAiImageOptions;
import org.springframework.util.Assert;

/**
 * Together AI image model implementation.
 *
 * @since 2.0.1
 * @author Maksym Uimanov
 */
public class TogetherAiImageModel implements ImageModel {

	private final TogetherAiImageOptions options;

	private final TogetherAiApi togetherAiApi;

	public TogetherAiImageModel(TogetherAiApi togetherAiApi) {
		this(togetherAiApi, TogetherAiImageOptions.builder().build());
	}

	public TogetherAiImageModel(TogetherAiApi togetherAiApi, TogetherAiImageOptions options) {
		Assert.notNull(togetherAiApi, "StabilityAiApi must not be null");
		Assert.notNull(options, "StabilityAiImageOptions must not be null");
		this.togetherAiApi = togetherAiApi;
		this.options = options;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		TogetherAiImageOptions requestImageOptions = mergeOptions(imagePrompt.getOptions(), this.options);
		TogetherAiApi.GenerateImageRequest generateImageRequest = getGenerateImageRequest(imagePrompt,
				requestImageOptions);
		TogetherAiApi.GenerateImageResponse generateImageResponse = this.togetherAiApi
			.generateImage(generateImageRequest);
		return convertResponse(generateImageResponse);
	}

	private TogetherAiImageOptions mergeOptions(@Nullable ImageOptions runtimeOptions, TogetherAiImageOptions options) {
		if (runtimeOptions == null) {
			return options;
		}
		TogetherAiImageOptions.Builder builder = TogetherAiImageOptions.builder()
			.model(ModelOptionsUtils.mergeOption(runtimeOptions, options, ImageOptions::getModel))
			.steps(options.getSteps())
			.imageUrl(options.getImageUrl())
			.seed(options.getSeed())
			.n(ModelOptionsUtils.mergeOption(runtimeOptions, options, ImageOptions::getN))
			.height(ModelOptionsUtils.mergeOption(runtimeOptions, options, ImageOptions::getHeight))
			.width(ModelOptionsUtils.mergeOption(runtimeOptions, options, ImageOptions::getWidth))
			.negativePrompt(options.getNegativePrompt())
			.responseFormat(ModelOptionsUtils.mergeOption(runtimeOptions, options,
					imageOptions -> TogetherAiImageOptions.ResponseFormat.from(imageOptions.getResponseFormat())))
			.guidanceScale(options.getGuidanceScale())
			.outputFormat(TogetherAiImageOptions.OutputFormat.from(options.getOutputFormat()))
			.imageLoras(options.getImageLoras())
			.referenceImages(options.getReferenceImages())
			.disableSafetyChecker(options.getDisableSafetyChecker());
		if (runtimeOptions instanceof TogetherAiImageOptions togetherAiImageOptions) {
			builder
				.steps(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options, TogetherAiImageOptions::getSteps))
				.imageUrl(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						TogetherAiImageOptions::getImageUrl))
				.seed(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options, TogetherAiImageOptions::getSeed))
				.negativePrompt(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						TogetherAiImageOptions::getNegativePrompt))
				.guidanceScale(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						TogetherAiImageOptions::getGuidanceScale))
				.outputFormat(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						imageOptions -> TogetherAiImageOptions.OutputFormat.from(imageOptions.getOutputFormat())))
				.imageLoras(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						TogetherAiImageOptions::getImageLoras))
				.referenceImages(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						TogetherAiImageOptions::getReferenceImages))
				.disableSafetyChecker(ModelOptionsUtils.mergeOption(togetherAiImageOptions, options,
						TogetherAiImageOptions::getDisableSafetyChecker));
		}
		return builder.build();
	}

	private TogetherAiApi.GenerateImageRequest getGenerateImageRequest(ImagePrompt imagePrompt,
			TogetherAiImageOptions imageOptions) {
		return TogetherAiApi.GenerateImageRequest.builder()
			.prompt(imagePrompt.getInstructions()
				.stream()
				.map(ImageMessage::toString)
				.collect(Collectors.joining("\n")))
			.model(imageOptions.getModel())
			.steps(imageOptions.getSteps())
			.imageUrl(imageOptions.getImageUrl())
			.seed(imageOptions.getSeed())
			.n(imageOptions.getN())
			.height(imageOptions.getHeight())
			.width(imageOptions.getWidth())
			.negativePrompt(imageOptions.getNegativePrompt())
			.responseFormat(imageOptions.getResponseFormat())
			.guidanceScale(imageOptions.getGuidanceScale())
			.outputFormat(imageOptions.getOutputFormat())
			.imageLoras(Objects.nonNull(imageOptions.getImageLoras()) ? imageOptions.getImageLoras()
				.stream()
				.map(TogetherAiApi.GenerateImageRequest.ImageLora::from)
				.toList() : null)
			.referenceImages(imageOptions.getReferenceImages())
			.disableSafetyChecker(imageOptions.getDisableSafetyChecker())
			.build();
	}

	private ImageResponse convertResponse(TogetherAiApi.GenerateImageResponse generateImageResponse) {
		List<ImageGeneration> imageGenerationList = generateImageResponse.data()
			.stream()
			.map(entry -> new ImageGeneration(new Image(entry.url(), entry.b64Json()),
					new TogetherAiImageGenerationMetadata(entry.index(),
							TogetherAiImageGenerationMetadata.ImageType.from(entry.type()))))
			.toList();
		return new ImageResponse(imageGenerationList, new ImageResponseMetadata());
	}

	public TogetherAiImageOptions getOptions() {
		return this.options;
	}

}
