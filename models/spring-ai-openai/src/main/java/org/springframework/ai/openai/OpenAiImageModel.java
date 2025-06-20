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

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
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
 * OpenAiImageModel is a class that implements the ImageModel interface. It provides a
 * client for calling the OpenAI image generation API.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @since 0.8.0
 */
public class OpenAiImageModel implements ImageModel {

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
	 * @deprecated use {@link OpenAiImageModel.Builder} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M8")
	public OpenAiImageModel(OpenAiImageApi openAiImageApi) {
		this(openAiImageApi, OpenAiImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiImageModel.
	 * @param openAiImageApi The OpenAiImageApi instance to be used for interacting with
	 * the OpenAI Image API.
	 * @param options The OpenAiImageOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 * @deprecated use {@link OpenAiImageModel.Builder} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M8")
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
	 * @deprecated use {@link OpenAiImageModel.Builder} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M8")
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
			.background(
					ModelOptionsUtils.mergeOption(runtimeOptions.getBackground(), this.defaultOptions.getBackground()))
			.moderation(
					ModelOptionsUtils.mergeOption(runtimeOptions.getModeration(), this.defaultOptions.getModeration()))
			.outputCompression(ModelOptionsUtils.mergeOption(runtimeOptions.getOutputCompression(),
					this.defaultOptions.getOutputCompression()))
			.outputFormat(ModelOptionsUtils.mergeOption(runtimeOptions.getOutputFormat(),
					this.defaultOptions.getOutputFormat()))
			.quality(ModelOptionsUtils.mergeOption(runtimeOptions.getQuality(), this.defaultOptions.getQuality()))
			.user(ModelOptionsUtils.mergeOption(runtimeOptions.getUser(), this.defaultOptions.getUser()))
			.build();

		return new ImagePrompt(imagePrompt.getInstructions(), requestOptions);
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static OpenAiImageModel.Builder builder() {
		return new OpenAiImageModel.Builder();
	}

	public static final class Builder {

		private OpenAiImageApi openAiImageApi;

		private OpenAiImageOptions defaultOptions = OpenAiImageOptions.builder().build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public OpenAiImageModel.Builder openAiImageApi(OpenAiImageApi openAiImageApi) {
			this.openAiImageApi = openAiImageApi;
			return this;
		}

		public OpenAiImageModel.Builder defaultOptions(OpenAiImageOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public OpenAiImageModel.Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public OpenAiImageModel.Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public OpenAiImageModel build() {
			return new OpenAiImageModel(this.openAiImageApi, this.defaultOptions, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
