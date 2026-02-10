/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.zhipuai;

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
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * ZhiPuAiImageModel is a class that implements the ImageModel interface. It provides a
 * client for calling the ZhiPuAI image generation API.
 *
 * @author Geng Rong
 * @author Yanming Zhou
 * @since 1.0.0 M1
 */
public class ZhiPuAiImageModel implements ImageModel {

	private static final Logger logger = LoggerFactory.getLogger(ZhiPuAiImageModel.class);

	public final RetryTemplate retryTemplate;

	private final ZhiPuAiImageOptions defaultOptions;

	private final ZhiPuAiImageApi zhiPuAiImageApi;

	private final ObservationRegistry observationRegistry;

	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public ZhiPuAiImageModel(ZhiPuAiImageApi zhiPuAiImageApi) {
		this(zhiPuAiImageApi, ZhiPuAiImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ZhiPuAiImageModel(ZhiPuAiImageApi zhiPuAiImageApi, ZhiPuAiImageOptions defaultOptions,
			RetryTemplate retryTemplate) {
		this(zhiPuAiImageApi, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public ZhiPuAiImageModel(ZhiPuAiImageApi zhiPuAiImageApi, ZhiPuAiImageOptions defaultOptions,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(zhiPuAiImageApi, "ZhiPuAiImageApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.zhiPuAiImageApi = zhiPuAiImageApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public ZhiPuAiImageOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {

		String instructions = imagePrompt.getInstructions().get(0).getText();

		ZhiPuAiImageApi.ZhiPuAiImageRequest imageRequest = new ZhiPuAiImageApi.ZhiPuAiImageRequest(instructions,
				ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL);
		imageRequest = ModelOptionsUtils.merge(this.defaultOptions, imageRequest,
				ZhiPuAiImageApi.ZhiPuAiImageRequest.class);
		imageRequest = ModelOptionsUtils.merge(toZhiPuAiImageOptions(imagePrompt.getOptions()), imageRequest,
				ZhiPuAiImageApi.ZhiPuAiImageRequest.class);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(AiProvider.ZHIPUAI.name())
			.build();

		ZhiPuAiImageApi.ZhiPuAiImageRequest imageRequestToUse = imageRequest;
		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				// Make the request
				ResponseEntity<ZhiPuAiImageApi.ZhiPuAiImageResponse> imageResponseEntity = RetryUtils
					.execute(this.retryTemplate, () -> this.zhiPuAiImageApi.createImage(imageRequestToUse));

				// Convert to org.springframework.ai.model derived ImageResponse data type
				return convertResponse(imageResponseEntity, imageRequestToUse);
			});

	}

	private ImageResponse convertResponse(ResponseEntity<ZhiPuAiImageApi.ZhiPuAiImageResponse> imageResponseEntity,
			ZhiPuAiImageApi.ZhiPuAiImageRequest zhiPuAiImageRequest) {
		ZhiPuAiImageApi.ZhiPuAiImageResponse imageApiResponse = imageResponseEntity.getBody();
		if (imageApiResponse == null) {
			logger.warn("No image response returned for request: {}", zhiPuAiImageRequest);
			return new ImageResponse(List.of());
		}

		List<ImageGeneration> imageGenerationList = imageApiResponse.data()
			.stream()
			.map(entry -> new ImageGeneration(new Image(entry.url(), null)))
			.toList();

		return new ImageResponse(imageGenerationList);
	}

	/**
	 * Convert the {@link ImageOptions} into {@link ZhiPuAiImageOptions}.
	 * @param runtimeImageOptions the image options to use.
	 * @return the converted {@link ZhiPuAiImageOptions}.
	 */
	private ZhiPuAiImageOptions toZhiPuAiImageOptions(ImageOptions runtimeImageOptions) {
		ZhiPuAiImageOptions.Builder zhiPuAiImageOptionsBuilder = ZhiPuAiImageOptions.builder();
		if (runtimeImageOptions != null) {
			if (runtimeImageOptions.getModel() != null) {
				zhiPuAiImageOptionsBuilder.model(runtimeImageOptions.getModel());
			}
			if (runtimeImageOptions instanceof ZhiPuAiImageOptions runtimeZhiPuAiImageOptions) {
				if (runtimeZhiPuAiImageOptions.getUser() != null) {
					zhiPuAiImageOptionsBuilder.user(runtimeZhiPuAiImageOptions.getUser());
				}
			}
		}
		return zhiPuAiImageOptionsBuilder.build();
	}

}
