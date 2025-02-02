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

package org.springframework.ai.qianfan;

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
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.qianfan.api.QianFanConstants;
import org.springframework.ai.qianfan.api.QianFanImageApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * QianFanImageModel is a class that implements the ImageModel interface. It provides a
 * client for calling the QianFan image generation API.
 *
 * @author Geng Rong
 * @since 1.0
 */
public class QianFanImageModel implements ImageModel {

	private static final Logger logger = LoggerFactory.getLogger(QianFanImageModel.class);

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	/**
	 * The default options used for the image completion requests.
	 */
	private final QianFanImageOptions defaultOptions;

	/**
	 * The retry template used to retry the QianFan Image API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the QianFan Image API.
	 */
	private final QianFanImageApi qianFanImageApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates an instance of the QianFanImageModel.
	 * @param qianFanImageApi The QianFanImageApi instance to be used for interacting with
	 * the QianFan Image API.
	 * @throws IllegalArgumentException if qianFanImageApi is null
	 */
	public QianFanImageModel(QianFanImageApi qianFanImageApi) {
		this(qianFanImageApi, QianFanImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Creates an instance of the QianFanImageModel.
	 * @param qianFanImageApi The QianFanImageApi instance to be used for interacting with
	 * the QianFan Image API.
	 * @param options The QianFanImageOptions to configure the image model.
	 * @throws IllegalArgumentException if qianFanImageApi is null
	 */
	public QianFanImageModel(QianFanImageApi qianFanImageApi, QianFanImageOptions options) {
		this(qianFanImageApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Creates an instance of the QianFanImageModel.
	 * @param qianFanImageApi The QianFanImageApi instance to be used for interacting with
	 * the QianFan Image API.
	 * @param options The QianFanImageOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 * @throws IllegalArgumentException if qianFanImageApi is null
	 */
	public QianFanImageModel(QianFanImageApi qianFanImageApi, QianFanImageOptions options,
			RetryTemplate retryTemplate) {
		this(qianFanImageApi, options, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the QianFanImageModel.
	 * @param qianFanImageApi The QianFanImageApi instance to be used for interacting with
	 * the QianFan Image API.
	 * @param options The QianFanImageOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The ObservationRegistry used for instrumentation.
	 */
	public QianFanImageModel(QianFanImageApi qianFanImageApi, QianFanImageOptions options, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(qianFanImageApi, "QianFanImageApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.qianFanImageApi = qianFanImageApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		QianFanImageOptions requestImageOptions = mergeOptions(imagePrompt.getOptions(), this.defaultOptions);

		QianFanImageApi.QianFanImageRequest imageRequest = createRequest(imagePrompt, requestImageOptions);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(QianFanConstants.PROVIDER_NAME)
			.requestOptions(requestImageOptions)
			.build();

		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<QianFanImageApi.QianFanImageResponse> imageResponseEntity = this.retryTemplate
					.execute(ctx -> this.qianFanImageApi.createImage(imageRequest));

				ImageResponse imageResponse = convertResponse(imageResponseEntity, imageRequest);

				observationContext.setResponse(imageResponse);

				return imageResponse;
			});
	}

	private QianFanImageApi.QianFanImageRequest createRequest(ImagePrompt imagePrompt,
			QianFanImageOptions requestImageOptions) {
		String instructions = imagePrompt.getInstructions().get(0).getText();

		QianFanImageApi.QianFanImageRequest imageRequest = new QianFanImageApi.QianFanImageRequest(instructions,
				QianFanImageApi.DEFAULT_IMAGE_MODEL);

		return ModelOptionsUtils.merge(requestImageOptions, imageRequest, QianFanImageApi.QianFanImageRequest.class);
	}

	private ImageResponse convertResponse(ResponseEntity<QianFanImageApi.QianFanImageResponse> imageResponseEntity,
			QianFanImageApi.QianFanImageRequest qianFanImageRequest) {
		QianFanImageApi.QianFanImageResponse imageApiResponse = imageResponseEntity.getBody();
		if (imageApiResponse == null) {
			logger.warn("No image response returned for request: {}", qianFanImageRequest);
			return new ImageResponse(List.of());
		}

		List<ImageGeneration> imageGenerationList = imageApiResponse.data()
			.stream()
			.map(entry -> new ImageGeneration(new Image(null, entry.b64Image())))
			.toList();

		return new ImageResponse(imageGenerationList);
	}

	/**
	 * Convert the {@link ImageOptions} into {@link QianFanImageOptions}.
	 * @param runtimeImageOptions the image options to use.
	 * @param defaultOptions the default options.
	 * @return the converted {@link QianFanImageOptions}.
	 */
	private QianFanImageOptions mergeOptions(@Nullable ImageOptions runtimeImageOptions,
			QianFanImageOptions defaultOptions) {
		var runtimeOptionsForProvider = ModelOptionsUtils.copyToTarget(runtimeImageOptions, ImageOptions.class,
				QianFanImageOptions.class);

		if (runtimeOptionsForProvider == null) {
			return defaultOptions;
		}

		return QianFanImageOptions.builder()
			.model(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getModel(), defaultOptions.getModel()))
			.N(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getN(), defaultOptions.getN()))
			.model(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getModel(), defaultOptions.getModel()))
			.width(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getWidth(), defaultOptions.getWidth()))
			.height(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getHeight(), defaultOptions.getHeight()))
			.style(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getStyle(), defaultOptions.getStyle()))
			.user(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getUser(), defaultOptions.getUser()))
			.build();
	}

	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

}
