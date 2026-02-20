/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.modelslab;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageGenerationMetadata;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ImageGenerationModel;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.modelslab.api.ModelsLabApi;
import org.springframework.ai.modelslab.api.ModelsLabApiConstants;
import org.springframework.ai.modelslab.options.ModelsLabImageOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link ImageGenerationModel} implementation for {@literal ModelsLab}
 * backed by {@link ModelsLabApi}.
 *
 * @author Patcher
 * @since 2.0.0
 * @see ImageGenerationModel
 * @see ModelsLabApi
 */
public class ModelsLabImageGenerationModel implements ImageGenerationModel {

	private static final Logger logger = LoggerFactory.getLogger(ModelsLabImageGenerationModel.class);

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	/**
	 * The default options used for the image generation requests.
	 */
	private final ModelsLabImageOptions defaultOptions;

	/**
	 * The retry template used to retry the ModelsLab API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the ModelsLab API.
	 */
	private final ModelsLabApi modelsLabApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Maximum time to wait for async generation completion (in seconds).
	 */
	private final int maxWaitTime;

	/**
	 * Poll interval for checking async generation status (in milliseconds).
	 */
	private final int pollInterval;

	/**
	 * Creates a new ModelsLabImageGenerationModel instance.
	 *
	 * @param modelsLabApi The ModelsLab API client
	 * @param defaultOptions The default image options
	 * @param retryTemplate The retry template
	 * @param observationRegistry The observation registry
	 */
	public ModelsLabImageGenerationModel(ModelsLabApi modelsLabApi, ModelsLabImageOptions defaultOptions,
										 RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		this(modelsLabApi, defaultOptions, retryTemplate, observationRegistry, 60, 2000);
	}

	/**
	 * Creates a new ModelsLabImageGenerationModel instance with custom timing settings.
	 *
	 * @param modelsLabApi The ModelsLab API client
	 * @param defaultOptions The default image options
	 * @param retryTemplate The retry template
	 * @param observationRegistry The observation registry
	 * @param maxWaitTime Maximum time to wait for async operations (seconds)
	 * @param pollInterval Poll interval for async operations (milliseconds)
	 */
	public ModelsLabImageGenerationModel(ModelsLabApi modelsLabApi, ModelsLabImageOptions defaultOptions,
										 RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
										 int maxWaitTime, int pollInterval) {
		Assert.notNull(modelsLabApi, "modelsLabApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");

		this.modelsLabApi = modelsLabApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.maxWaitTime = maxWaitTime;
		this.pollInterval = pollInterval;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		return this.internalCall(imagePrompt);
	}

	/**
	 * Internal call method to handle the actual API request.
	 *
	 * @param imagePrompt The image generation prompt
	 * @return The image response
	 */
	public ImageResponse internalCall(ImagePrompt imagePrompt) {
		Assert.notNull(imagePrompt, "ImagePrompt must not be null");
		Assert.hasText(imagePrompt.getInstructions().get(0).getText(), "Prompt text must not be empty");

		ModelsLabApi.TextToImageRequest request = createRequest(imagePrompt);

		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(ModelsLabApiConstants.PROVIDER_NAME)
			.build();

		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
				this.observationRegistry)
			.observe(() -> {
				ResponseEntity<ModelsLabApi.ImageGenerationResponse> responseEntity = 
					RetryUtils.execute(this.retryTemplate, () -> this.modelsLabApi.textToImageEntity(request));

				ModelsLabApi.ImageGenerationResponse response = responseEntity.getBody();

				if (response == null) {
					logger.warn("No image generation response returned for prompt: {}", imagePrompt);
					return new ImageResponse(List.of());
				}

				// Handle async processing if needed
				if (ModelsLabApiConstants.STATUS_PROCESSING.equals(response.status()) && response.id() != null) {
					logger.debug("Image generation is processing asynchronously, polling for result. ID: {}", response.id());
					response = pollForCompletion(response.id().toString());
				}

				if (!ModelsLabApiConstants.STATUS_SUCCESS.equals(response.status())) {
					String errorMessage = response.message() != null ? response.message() : "Unknown error";
					logger.error("Image generation failed: {}", errorMessage);
					throw new RuntimeException("Image generation failed: " + errorMessage);
				}

				List<ImageGeneration> imageGenerations = response.output().stream()
					.map(this::createImageGeneration)
					.toList();

				ImageResponse imageResponse = new ImageResponse(imageGenerations, 
					buildImageResponseMetadata(response));

				observationContext.setResponse(imageResponse);

				return imageResponse;
			});
	}

	/**
	 * Polls for async operation completion.
	 *
	 * @param requestId The request ID to poll for
	 * @return The completed response
	 */
	private ModelsLabApi.ImageGenerationResponse pollForCompletion(String requestId) {
		long startTime = System.currentTimeMillis();
		long maxWaitMillis = maxWaitTime * 1000L;

		while (System.currentTimeMillis() - startTime < maxWaitMillis) {
			try {
				ResponseEntity<ModelsLabApi.ImageGenerationResponse> responseEntity = 
					RetryUtils.execute(this.retryTemplate, () -> this.modelsLabApi.fetchImageResult(requestId));

				ModelsLabApi.ImageGenerationResponse response = responseEntity.getBody();

				if (response != null && ModelsLabApiConstants.STATUS_SUCCESS.equals(response.status())) {
					logger.debug("Async image generation completed. ID: {}", requestId);
					return response;
				}

				if (response != null && ModelsLabApiConstants.STATUS_ERROR.equals(response.status())) {
					String errorMessage = response.message() != null ? response.message() : "Unknown error";
					throw new RuntimeException("Async image generation failed: " + errorMessage);
				}

				// Still processing, wait before next poll
				Thread.sleep(pollInterval);

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Image generation polling interrupted", e);
			}
		}

		throw new RuntimeException("Image generation timed out after " + maxWaitTime + " seconds");
	}

	/**
	 * Creates an ImageGeneration from a ModelsLab image URL.
	 *
	 * @param imageUrl The image URL from ModelsLab
	 * @return An ImageGeneration instance
	 */
	private ImageGeneration createImageGeneration(String imageUrl) {
		return new ImageGeneration(
			new Image(imageUrl, null),
			ImageGenerationMetadata.builder()
				.revised_prompt(null) // ModelsLab doesn't provide revised prompts
				.build()
		);
	}

	/**
	 * Builds ImageResponseMetadata from the ModelsLab response.
	 *
	 * @param response The ModelsLab response
	 * @return ImageResponseMetadata
	 */
	private ImageResponseMetadata buildImageResponseMetadata(ModelsLabApi.ImageGenerationResponse response) {
		return ImageResponseMetadata.builder()
			.created(System.currentTimeMillis() / 1000L) // Current timestamp as created time
			.build();
	}

	/**
	 * Builds the request from an ImagePrompt.
	 *
	 * @param imagePrompt The image prompt
	 * @return A TextToImageRequest
	 */
	ModelsLabApi.TextToImageRequest createRequest(ImagePrompt imagePrompt) {
		String prompt = imagePrompt.getInstructions().get(0).getText();

		// Build request options by merging runtime and default options
		ModelsLabImageOptions requestOptions = buildRequestOptions(imagePrompt);

		return new ModelsLabApi.TextToImageRequest(
			null, // API key will be added by the API client
			requestOptions.getModel(),
			prompt,
			requestOptions.getNegativePrompt(),
			requestOptions.getWidth(),
			requestOptions.getHeight(),
			requestOptions.getN(),
			requestOptions.getNumInferenceSteps(),
			requestOptions.getGuidanceScale(),
			requestOptions.getSeed(),
			requestOptions.getSafetyChecker(),
			requestOptions.getWebhook(),
			requestOptions.getTrackId()
		);
	}

	/**
	 * Builds request options by merging runtime and default options.
	 *
	 * @param imagePrompt The image prompt containing options
	 * @return Merged options
	 */
	ModelsLabImageOptions buildRequestOptions(ImagePrompt imagePrompt) {
		ModelsLabImageOptions runtimeOptions = null;
		if (imagePrompt.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(imagePrompt.getOptions(), ImageOptions.class,
				ModelsLabImageOptions.class);
		}

		// Merge runtime options with default options
		return ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, ModelsLabImageOptions.class);
	}

	@Override
	public ImageOptions getDefaultOptions() {
		return ModelsLabImageOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public String toString() {
		return "ModelsLabImageGenerationModel [defaultOptions=" + this.defaultOptions + "]";
	}

	/**
	 * Use the provided convention for reporting observation data.
	 *
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Creates a new builder.
	 *
	 * @return A new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a builder pre-populated with the current configuration for mutation.
	 *
	 * @return A builder with current configuration
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	/**
	 * Builder for ModelsLabImageGenerationModel.
	 */
	public static final class Builder {

		private ModelsLabApi modelsLabApi;
		private ModelsLabImageOptions defaultOptions = ModelsLabImageOptions.builder()
			.model(ModelsLabApiConstants.DEFAULT_IMAGE_MODEL)
			.width(1024)
			.height(1024)
			.n(1)
			.build();
		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		private int maxWaitTime = 60;
		private int pollInterval = 2000;

		private Builder() {
		}

		// Copy constructor for mutate()
		public Builder(ModelsLabImageGenerationModel model) {
			this.modelsLabApi = model.modelsLabApi;
			this.defaultOptions = model.defaultOptions;
			this.retryTemplate = model.retryTemplate;
			this.observationRegistry = model.observationRegistry;
			this.maxWaitTime = model.maxWaitTime;
			this.pollInterval = model.pollInterval;
		}

		public Builder modelsLabApi(ModelsLabApi modelsLabApi) {
			this.modelsLabApi = modelsLabApi;
			return this;
		}

		public Builder defaultOptions(ModelsLabImageOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder maxWaitTime(int maxWaitTime) {
			this.maxWaitTime = maxWaitTime;
			return this;
		}

		public Builder pollInterval(int pollInterval) {
			this.pollInterval = pollInterval;
			return this;
		}

		public ModelsLabImageGenerationModel build() {
			return new ModelsLabImageGenerationModel(this.modelsLabApi, this.defaultOptions,
				this.retryTemplate, this.observationRegistry, this.maxWaitTime, this.pollInterval);
		}
	}

}