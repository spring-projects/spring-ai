package org.springframework.ai.stabilityai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptionsBuilder;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Collectors;

public class StabilityAiImageClient implements ImageClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private StabilityAiImageOptions options;

	private final StabilityAiApi stabilityAiApi;

	public StabilityAiImageClient(StabilityAiApi stabilityAiApi) {
		this(stabilityAiApi, StabilityAiImageOptionsBuilder.builder().build());
	}

	public StabilityAiImageClient(StabilityAiApi stabilityAiApi, StabilityAiImageOptions options) {
		Assert.notNull(stabilityAiApi, "StabilityAiApi must not be null");
		Assert.notNull(options, "StabilityAiImageOptions must not be null");
		this.stabilityAiApi = stabilityAiApi;
		this.options = options;
	}

	public StabilityAiImageOptions getOptions() {
		return options;
	}

	@Override
	public ImageResponse call(ImagePrompt prompt) {
		StabilityAiImageOptions options = convertOptions(prompt.getOptions());
		List<StabilityAiImageMessage> messages = convertInstructions(prompt.getInstructions());
		return call(new StabilityAiImagePrompt(messages, options));
	}

	public ImageResponse call(StabilityAiImagePrompt stabilityAiImagePrompt) {
		StabilityAiApi.GenerateImageRequest.Builder builder = new StabilityAiApi.GenerateImageRequest.Builder();
		StabilityAiImageOptions options = stabilityAiImagePrompt.getOptions();
		StabilityAiApi.GenerateImageRequest generateImageRequest = builder
			.withTextPrompts(stabilityAiImagePrompt.getInstructions()
				.stream()
				.map(message -> new StabilityAiApi.GenerateImageRequest.TextPrompts(message.getText(),
						message.getWeight()))
				.collect(Collectors.toList()))
			.withHeight(options.getHeight())
			.withWidth(options.getWidth())
			.withCfgScale(options.getCfgScale())
			.withClipGuidancePreset(options.getClipGuidancePreset())
			.withSampler(options.getSampler())
			.withSamples(options.getSamples())
			.withSeed(options.getSeed())
			.withSteps(options.getSteps())
			.withStylePreset(options.getStylePreset())
			.build();
		StabilityAiApi.GenerateImageResponse generateImageResponse = this.stabilityAiApi
			.generateImage(generateImageRequest);
		return convertResponse(generateImageResponse);

	}

	private ImageResponse convertResponse(StabilityAiApi.GenerateImageResponse generateImageResponse) {
		List<ImageGeneration> imageGenerationList = generateImageResponse.artifacts().stream().map(entry -> {
			return new ImageGeneration(new Image(null, entry.base64()),
					new StabilityAiImageGenerationMetadata(entry.finishReason(), entry.seed()));
		}).toList();

		return new ImageResponse(imageGenerationList, ImageResponseMetadata.NULL);
	}

	private List<StabilityAiImageMessage> convertInstructions(String instructions) {
		StabilityAiImageMessage stabilityAiImageMessage = new StabilityAiImageMessage(instructions, 0.5F);
		return List.of(stabilityAiImageMessage);
	}

	private StabilityAiImageOptions convertOptions(ImageOptions runtimeOptions) {
		// Replace with JSON utility
		StabilityAiImageOptionsBuilder builder = StabilityAiImageOptionsBuilder.builder();
		if (runtimeOptions.getN() != null) {
			builder.withN(runtimeOptions.getN());
		}
		if (runtimeOptions.getModel() != null) {
			builder.withModel(runtimeOptions.getModel());
		}
		if (runtimeOptions.getResponseFormat() != null) {
			builder.withResponseFormat(runtimeOptions.getResponseFormat());
		}
		if (runtimeOptions.getWidth() != null) {
			builder.withWidth(runtimeOptions.getWidth());
		}
		if (runtimeOptions.getHeight() != null) {
			builder.withHeight(runtimeOptions.getHeight());
		}
		if (runtimeOptions instanceof StabilityAiImageOptions) {
			StabilityAiImageOptions stabilityAiImageOptions = (StabilityAiImageOptions) runtimeOptions;
			if (stabilityAiImageOptions.getCfgScale() != null) {
				builder.withCfgScale(stabilityAiImageOptions.getCfgScale());
			}
			if (stabilityAiImageOptions.getClipGuidancePreset() != null) {
				builder.withClipGuidancePreset(stabilityAiImageOptions.getClipGuidancePreset());
			}
			if (stabilityAiImageOptions.getSampler() != null) {
				builder.withSampler(stabilityAiImageOptions.getSampler());
			}
			if (stabilityAiImageOptions.getSeed() != null) {
				builder.withSeed(stabilityAiImageOptions.getSeed());
			}
			if (stabilityAiImageOptions.getSteps() != null) {
				builder.withSteps(stabilityAiImageOptions.getSteps());
			}
			if (stabilityAiImageOptions.getStylePreset() != null) {
				builder.withStylePreset(stabilityAiImageOptions.getStylePreset());
			}
		}
		return builder.build();
	}

	private ImagePrompt createUpdatedPrompt(ImagePrompt prompt) {
		ImageOptions runtimeImageModelOptions = prompt.getOptions();
		ImageOptionsBuilder imageOptionsBuilder = ImageOptionsBuilder.builder();

		if (runtimeImageModelOptions != null) {
			if (runtimeImageModelOptions.getModel() != null) {
				imageOptionsBuilder.withModel(runtimeImageModelOptions.getModel());
			}
		}
		ImageOptions updatedImageModelOptions = imageOptionsBuilder.build();
		return new ImagePrompt(prompt.getInstructions(), updatedImageModelOptions);
	}

}
