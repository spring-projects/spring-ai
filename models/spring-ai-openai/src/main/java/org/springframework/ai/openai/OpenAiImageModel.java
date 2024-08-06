/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai;

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
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.image.observation.ImageModelRequestOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * OpenAiImageModel is a class that implements the ImageModel interface. It provides a
 * client for calling the OpenAI image generation API.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @since 0.8.0
 */
public class OpenAiImageModel implements ImageModel {

	private final static Logger logger = LoggerFactory.getLogger(OpenAiImageModel.class);

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
		OpenAiImageApi.OpenAiImageRequest imageRequest = createRequest(imagePrompt);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.operationMetadata(buildOperationMetadata())
			.requestOptions(buildRequestOptions(imageRequest))
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

		OpenAiImageApi.OpenAiImageRequest imageRequest = new OpenAiImageApi.OpenAiImageRequest(instructions,
				OpenAiImageApi.DEFAULT_IMAGE_MODEL);

		if (this.defaultOptions != null) {
			imageRequest = ModelOptionsUtils.merge(this.defaultOptions, imageRequest,
					OpenAiImageApi.OpenAiImageRequest.class);
		}

		if (imagePrompt.getOptions() != null) {
			imageRequest = ModelOptionsUtils.merge(toOpenAiImageOptions(imagePrompt.getOptions()), imageRequest,
					OpenAiImageApi.OpenAiImageRequest.class);
		}

		return imageRequest;
	}

	private ImageResponse convertResponse(ResponseEntity<OpenAiImageApi.OpenAiImageResponse> imageResponseEntity,
			OpenAiImageApi.OpenAiImageRequest openAiImageRequest) {
		OpenAiImageApi.OpenAiImageResponse imageApiResponse = imageResponseEntity.getBody();
		if (imageApiResponse == null) {
			logger.warn("No image response returned for request: {}", openAiImageRequest);
			return new ImageResponse(List.of());
		}

		List<ImageGeneration> imageGenerationList = imageApiResponse.data().stream().map(entry -> {
			return new ImageGeneration(new Image(entry.url(), entry.b64Json()),
					new OpenAiImageGenerationMetadata(entry.revisedPrompt()));
		}).toList();

		ImageResponseMetadata openAiImageResponseMetadata = new ImageResponseMetadata(imageApiResponse.created());
		return new ImageResponse(imageGenerationList, openAiImageResponseMetadata);
	}

	/**
	 * Convert the {@link ImageOptions} into {@link OpenAiImageOptions}.
	 * @param runtimeImageOptions the image options to use.
	 * @return the converted {@link OpenAiImageOptions}.
	 */
	private OpenAiImageOptions toOpenAiImageOptions(ImageOptions runtimeImageOptions) {
		OpenAiImageOptions.Builder openAiImageOptionsBuilder = OpenAiImageOptions.builder();
		if (runtimeImageOptions != null) {
			// Handle portable image options
			if (runtimeImageOptions.getN() != null) {
				openAiImageOptionsBuilder.withN(runtimeImageOptions.getN());
			}
			if (runtimeImageOptions.getModel() != null) {
				openAiImageOptionsBuilder.withModel(runtimeImageOptions.getModel());
			}
			if (runtimeImageOptions.getResponseFormat() != null) {
				openAiImageOptionsBuilder.withResponseFormat(runtimeImageOptions.getResponseFormat());
			}
			if (runtimeImageOptions.getWidth() != null) {
				openAiImageOptionsBuilder.withWidth(runtimeImageOptions.getWidth());
			}
			if (runtimeImageOptions.getHeight() != null) {
				openAiImageOptionsBuilder.withHeight(runtimeImageOptions.getHeight());
			}
			// Handle OpenAI specific image options
			if (runtimeImageOptions instanceof OpenAiImageOptions) {
				OpenAiImageOptions runtimeOpenAiImageOptions = (OpenAiImageOptions) runtimeImageOptions;
				if (runtimeOpenAiImageOptions.getQuality() != null) {
					openAiImageOptionsBuilder.withQuality(runtimeOpenAiImageOptions.getQuality());
				}
				if (runtimeOpenAiImageOptions.getStyle() != null) {
					openAiImageOptionsBuilder.withStyle(runtimeOpenAiImageOptions.getStyle());
				}
				if (runtimeOpenAiImageOptions.getUser() != null) {
					openAiImageOptionsBuilder.withUser(runtimeOpenAiImageOptions.getUser());
				}
			}
		}
		return openAiImageOptionsBuilder.build();
	}

	private AiOperationMetadata buildOperationMetadata() {
		return AiOperationMetadata.builder()
			.operationType(AiOperationType.IMAGE.value())
			.provider(AiProvider.OPENAI.value())
			.build();
	}

	private ImageModelRequestOptions buildRequestOptions(OpenAiImageApi.OpenAiImageRequest request) {
		return ImageModelRequestOptions.builder()
			.model(StringUtils.hasText(request.model()) ? request.model() : "unknown")
			.n(request.n())
			.width(request.size() != null ? Integer.parseInt(request.size().split("x")[0]) : null)
			.height(request.size() != null ? Integer.parseInt(request.size().split("x")[1]) : null)
			.responseFormat(request.responseFormat())
			.style(request.style())
			.build();
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
