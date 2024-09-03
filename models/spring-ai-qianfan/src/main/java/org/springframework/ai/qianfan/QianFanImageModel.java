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
package org.springframework.ai.qianfan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.qianfan.api.QianFanImageApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.List;

/**
 * QianFanImageModel is a class that implements the ImageModel interface. It provides a
 * client for calling the QianFan image generation API.
 *
 * @author Geng Rong
 * @since 1.0
 */
public class QianFanImageModel implements ImageModel {

	private final static Logger logger = LoggerFactory.getLogger(QianFanImageModel.class);

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
	 * Creates an instance of the QianFanImageModel.
	 * @param qianFanImageApi The QianFanImageApi instance to be used for interacting with
	 * the QianFan Image API.
	 * @throws IllegalArgumentException if qianFanImageApi is null
	 */
	public QianFanImageModel(QianFanImageApi qianFanImageApi) {
		this(qianFanImageApi, QianFanImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the QianFanImageModel.
	 * @param qianFanImageApi The QianFanImageApi instance to be used for interacting with
	 * the QianFan Image API.
	 * @param options The QianFanImageOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 */
	public QianFanImageModel(QianFanImageApi qianFanImageApi, QianFanImageOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(qianFanImageApi, "QianFanImageApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		this.qianFanImageApi = qianFanImageApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		return this.retryTemplate.execute(ctx -> {

			String instructions = imagePrompt.getInstructions().get(0).getText();

			QianFanImageApi.QianFanImageRequest imageRequest = new QianFanImageApi.QianFanImageRequest(instructions,
					QianFanImageApi.DEFAULT_IMAGE_MODEL);

			if (this.defaultOptions != null) {
				imageRequest = ModelOptionsUtils.merge(this.defaultOptions, imageRequest,
						QianFanImageApi.QianFanImageRequest.class);
			}

			if (imagePrompt.getOptions() != null) {
				imageRequest = ModelOptionsUtils.merge(toQianFanImageOptions(imagePrompt.getOptions()), imageRequest,
						QianFanImageApi.QianFanImageRequest.class);
			}

			// Make the request
			ResponseEntity<QianFanImageApi.QianFanImageResponse> imageResponseEntity = this.qianFanImageApi
				.createImage(imageRequest);

			// Convert to org.springframework.ai.model derived ImageResponse data type
			return convertResponse(imageResponseEntity, imageRequest);
		});
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
	 * @return the converted {@link QianFanImageOptions}.
	 */
	private QianFanImageOptions toQianFanImageOptions(ImageOptions runtimeImageOptions) {
		QianFanImageOptions.Builder qianFanImageOptionsBuilder = QianFanImageOptions.builder();
		if (runtimeImageOptions != null) {
			if (runtimeImageOptions.getN() != null) {
				qianFanImageOptionsBuilder.withN(runtimeImageOptions.getN());
			}
			if (runtimeImageOptions.getModel() != null) {
				qianFanImageOptionsBuilder.withModel(runtimeImageOptions.getModel());
			}
			if (runtimeImageOptions.getWidth() != null) {
				qianFanImageOptionsBuilder.withWidth(runtimeImageOptions.getWidth());
			}
			if (runtimeImageOptions.getHeight() != null) {
				qianFanImageOptionsBuilder.withHeight(runtimeImageOptions.getHeight());
			}
			if (runtimeImageOptions instanceof QianFanImageOptions runtimeQianFanImageOptions) {
				if (runtimeQianFanImageOptions.getStyle() != null) {
					qianFanImageOptionsBuilder.withStyle(runtimeQianFanImageOptions.getStyle());
				}
				if (runtimeQianFanImageOptions.getUser() != null) {
					qianFanImageOptionsBuilder.withUser(runtimeQianFanImageOptions.getUser());
				}
			}
		}
		return qianFanImageOptionsBuilder.build();
	}

}
