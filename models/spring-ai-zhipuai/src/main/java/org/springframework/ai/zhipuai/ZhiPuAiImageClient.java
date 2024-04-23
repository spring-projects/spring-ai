/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.zhipuai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.List;

/**
 * ZhiPuAiImageClient is a class that implements the ImageClient interface. It provides a
 * client for calling the ZhiPuAI image generation API.
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class ZhiPuAiImageClient implements ImageClient {

	private final static Logger logger = LoggerFactory.getLogger(ZhiPuAiImageClient.class);

	private final ZhiPuAiImageOptions defaultOptions;

	private final ZhiPuAiImageApi zhiPuAiImageApi;

	public final RetryTemplate retryTemplate;

	public ZhiPuAiImageClient(ZhiPuAiImageApi zhiPuAiImageApi) {
		this(zhiPuAiImageApi, ZhiPuAiImageOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ZhiPuAiImageClient(ZhiPuAiImageApi zhiPuAiImageApi, ZhiPuAiImageOptions defaultOptions,
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
		return this.retryTemplate.execute(ctx -> {

			String instructions = imagePrompt.getInstructions().get(0).getText();

			ZhiPuAiImageApi.ZhiPuAiImageRequest imageRequest = new ZhiPuAiImageApi.ZhiPuAiImageRequest(instructions,
					ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL);

			if (this.defaultOptions != null) {
				imageRequest = ModelOptionsUtils.merge(this.defaultOptions, imageRequest,
						ZhiPuAiImageApi.ZhiPuAiImageRequest.class);
			}

			if (imagePrompt.getOptions() != null) {
				imageRequest = ModelOptionsUtils.merge(toZhiPuAiImageOptions(imagePrompt.getOptions()), imageRequest,
						ZhiPuAiImageApi.ZhiPuAiImageRequest.class);
			}

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

	/**
	 * Convert the {@link ImageOptions} into {@link ZhiPuAiImageOptions}.
	 * @param runtimeImageOptions the image options to use.
	 * @return the converted {@link ZhiPuAiImageOptions}.
	 */
	private ZhiPuAiImageOptions toZhiPuAiImageOptions(ImageOptions runtimeImageOptions) {
		ZhiPuAiImageOptions.Builder zhiPuAiImageOptionsBuilder = ZhiPuAiImageOptions.builder();
		if (runtimeImageOptions != null) {
			if (runtimeImageOptions.getModel() != null) {
				zhiPuAiImageOptionsBuilder.withModel(runtimeImageOptions.getModel());
			}
			if (runtimeImageOptions instanceof ZhiPuAiImageOptions runtimeZhiPuAiImageOptions) {
				if (runtimeZhiPuAiImageOptions.getUser() != null) {
					zhiPuAiImageOptionsBuilder.withUser(runtimeZhiPuAiImageOptions.getUser());
				}
			}
		}
		return zhiPuAiImageOptionsBuilder.build();
	}

}
