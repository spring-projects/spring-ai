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

package org.springframework.ai.zhipuai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.ModelOptionsUtils;
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

	public ZhiPuAiImageModel(ZhiPuAiImageApi zhiPuAiImageApi) {
		this(zhiPuAiImageApi, ZhiPuAiImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ZhiPuAiImageModel(ZhiPuAiImageApi zhiPuAiImageApi, ZhiPuAiImageOptions defaultOptions,
			RetryTemplate retryTemplate) {
		Assert.notNull(zhiPuAiImageApi, "ZhiPuAiImageApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		this.zhiPuAiImageApi = zhiPuAiImageApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
	}

	public ZhiPuAiImageOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {

		return RetryUtils.execute(this.retryTemplate, () -> {

			String instructions = imagePrompt.getInstructions().get(0).getText();

			ZhiPuAiImageApi.ZhiPuAiImageRequest imageRequest = new ZhiPuAiImageApi.ZhiPuAiImageRequest(instructions,
					ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL);
			imageRequest = ModelOptionsUtils.merge(
					ModelOptionsUtils.merge(imagePrompt.getOptions(), this.defaultOptions, ZhiPuAiImageOptions.class),
					imageRequest, ZhiPuAiImageApi.ZhiPuAiImageRequest.class);

			// Make the request
			ResponseEntity<ZhiPuAiImageApi.ZhiPuAiImageResponse> imageResponseEntity = this.zhiPuAiImageApi
				.createImage(imageRequest);

			// Convert to org.springframework.ai.model derived ImageResponse data type
			return convertResponse(imageResponseEntity, imageRequest);
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

}
