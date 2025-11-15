/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openai;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.StreamingImageModel;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * OpenAiImageModel is a class that implements the ImageModel and StreamingImageModel
 * interfaces. It provides a client for calling the OpenAI image generation API with both
 * synchronous and streaming capabilities.
 *
 * <p>
 * Streaming image generation is supported for GPT-Image models (gpt-image-1,
 * gpt-image-1-mini) and allows receiving partial images as they are generated. DALL-E
 * models do not support streaming.
 * </p>
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @since 0.8.0
 * @see ImageModel
 * @see StreamingImageModel
 */
public class OpenAiImageModel implements ImageModel, StreamingImageModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiImageModel.class);

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	/**
	 * The default options used for the image completion requests.
	 */
	private final OpenAiImageOptions defaultOptions;

	/**
	 * The retry template used to retry the OpenAI Image API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the OpenAI Image API.
	 */
	private final OpenAiImageApi openAiImageApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates an instance of the OpenAiImageModel.
	 * @param openAiImageApi The OpenAiImageApi instance to be used for interacting with
	 * the OpenAI Image API.
	 * @throws IllegalArgumentException if openAiImageApi is null
	 */
	public OpenAiImageModel(OpenAiImageApi openAiImageApi) {
		this(openAiImageApi, OpenAiImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiImageModel.
	 * @param openAiImageApi The OpenAiImageApi instance to be used for interacting with
	 * the OpenAI Image API.
	 * @param options The OpenAiImageOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 */
	public OpenAiImageModel(OpenAiImageApi openAiImageApi, OpenAiImageOptions options, RetryTemplate retryTemplate) {
		this(openAiImageApi, options, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the OpenAiImageModel.
	 * @param openAiImageApi The OpenAiImageApi instance to be used for interacting with
	 * the OpenAI Image API.
	 * @param options The OpenAiImageOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The ObservationRegistry used for instrumentation.
	 */
	public OpenAiImageModel(OpenAiImageApi openAiImageApi, OpenAiImageOptions options, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(openAiImageApi, "OpenAiImageApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.openAiImageApi = openAiImageApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		// Before moving any further, build the final request ImagePrompt,
		// merging runtime and default options.
		ImagePrompt requestImagePrompt = buildRequestImagePrompt(imagePrompt);

		OpenAiImageApi.OpenAiImageRequest imageRequest = createRequest(requestImagePrompt);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(OpenAiApiConstants.PROVIDER_NAME)
			.build();

		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ResponseEntity<OpenAiImageApi.OpenAiImageResponse> imageResponseEntity = this.retryTemplate
					.execute(ctx -> this.openAiImageApi.createImage(imageRequest));

				ImageResponse imageResponse = convertResponse(imageResponseEntity, imageRequest);

				observationContext.setResponse(imageResponse);

				return imageResponse;
			});
	}

	private OpenAiImageApi.OpenAiImageRequest createRequest(ImagePrompt imagePrompt) {
		String instructions = imagePrompt.getInstructions().get(0).getText();
		OpenAiImageOptions imageOptions = (OpenAiImageOptions) imagePrompt.getOptions();

		OpenAiImageApi.OpenAiImageRequest imageRequest = new OpenAiImageApi.OpenAiImageRequest(instructions,
				OpenAiImageApi.DEFAULT_IMAGE_MODEL);

		return ModelOptionsUtils.merge(imageOptions, imageRequest, OpenAiImageApi.OpenAiImageRequest.class);
	}

	private ImageResponse convertResponse(ResponseEntity<OpenAiImageApi.OpenAiImageResponse> imageResponseEntity,
			OpenAiImageApi.OpenAiImageRequest openAiImageRequest) {
		OpenAiImageApi.OpenAiImageResponse imageApiResponse = imageResponseEntity.getBody();
		if (imageApiResponse == null) {
			logger.warn("No image response returned for request: {}", openAiImageRequest);
			return new ImageResponse(List.of());
		}

		List<ImageGeneration> imageGenerationList = imageApiResponse.data()
			.stream()
			.map(entry -> new ImageGeneration(new Image(entry.url(), entry.b64Json()),
					new OpenAiImageGenerationMetadata(entry.revisedPrompt())))
			.toList();

		ImageResponseMetadata openAiImageResponseMetadata = new ImageResponseMetadata(imageApiResponse.created());
		return new ImageResponse(imageGenerationList, openAiImageResponseMetadata);
	}

	private ImagePrompt buildRequestImagePrompt(ImagePrompt imagePrompt) {
		// Process runtime options
		OpenAiImageOptions runtimeOptions = null;
		if (imagePrompt.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(imagePrompt.getOptions(), ImageOptions.class,
					OpenAiImageOptions.class);
		}

		OpenAiImageOptions requestOptions = runtimeOptions == null ? this.defaultOptions : OpenAiImageOptions.builder()
			// Handle portable image options
			.model(ModelOptionsUtils.mergeOption(runtimeOptions.getModel(), this.defaultOptions.getModel()))
			.N(ModelOptionsUtils.mergeOption(runtimeOptions.getN(), this.defaultOptions.getN()))
			.responseFormat(ModelOptionsUtils.mergeOption(runtimeOptions.getResponseFormat(),
					this.defaultOptions.getResponseFormat()))
			.width(ModelOptionsUtils.mergeOption(runtimeOptions.getWidth(), this.defaultOptions.getWidth()))
			.height(ModelOptionsUtils.mergeOption(runtimeOptions.getHeight(), this.defaultOptions.getHeight()))
			.style(ModelOptionsUtils.mergeOption(runtimeOptions.getStyle(), this.defaultOptions.getStyle()))
			// Handle OpenAI specific image options
			.quality(ModelOptionsUtils.mergeOption(runtimeOptions.getQuality(), this.defaultOptions.getQuality()))
			.user(ModelOptionsUtils.mergeOption(runtimeOptions.getUser(), this.defaultOptions.getUser()))
			.build();

		return new ImagePrompt(imagePrompt.getInstructions(), requestOptions);
	}

	@Override
	public Flux<ImageResponse> stream(ImagePrompt imagePrompt) {
		// Before moving any further, build the final request ImagePrompt,
		// merging runtime and default options.
		ImagePrompt requestImagePrompt = buildRequestImagePrompt(imagePrompt);

		OpenAiImageApi.OpenAiImageRequest imageRequest = createRequest(requestImagePrompt);

		// Validate that streaming is only used with GPT-Image models
		String model = imageRequest.model();
		if (model != null && !model.startsWith("gpt-image-")) {
			return Flux.error(new IllegalArgumentException(
					"Streaming is only supported for GPT-Image models (gpt-image-1, gpt-image-1-mini). "
							+ "Current model: " + model));
		}

		// Ensure stream is set to true
		if (imageRequest.stream() == null || !imageRequest.stream()) {
			imageRequest = new OpenAiImageApi.OpenAiImageRequest(imageRequest.prompt(), imageRequest.model(),
					imageRequest.n(), imageRequest.quality(), imageRequest.responseFormat(), imageRequest.size(),
					imageRequest.style(), imageRequest.user(), imageRequest.background(), imageRequest.moderation(),
					imageRequest.outputCompression(), imageRequest.outputFormat(), imageRequest.partialImages(), true);
		}

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(OpenAiApiConstants.PROVIDER_NAME)
			.build();

		OpenAiImageApi.OpenAiImageRequest finalImageRequest = imageRequest;

		// Stream the image generation events
		Flux<OpenAiImageApi.OpenAiImageStreamEvent> eventStream = this.openAiImageApi.streamImage(finalImageRequest);

		// Convert streaming events to ImageResponse
		return eventStream.map(event -> {
			Image image = new Image(null, event.b64Json());
			OpenAiImageGenerationMetadata metadata = new OpenAiImageGenerationMetadata(null);
			ImageGeneration generation = new ImageGeneration(image, metadata);
			ImageResponseMetadata responseMetadata = event.createdAt() != null
					? new ImageResponseMetadata(event.createdAt()) : new ImageResponseMetadata(null);
			return new ImageResponse(List.of(generation), responseMetadata);
		});
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

}
