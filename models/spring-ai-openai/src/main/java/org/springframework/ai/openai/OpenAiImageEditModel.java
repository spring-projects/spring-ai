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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageEditModel;
import org.springframework.ai.image.ImageEditOptions;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageEditPrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiImageApi.ImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageEditRequest;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageResponse;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * OpenAiImageEditModel is a class that implements the ImageModel interface. It provides a
 * client for calling the OpenAI image edit API.
 *
 * @author Minsoo Nam
 * @since 1.0.0
 */
public class OpenAiImageEditModel implements ImageEditModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiImageEditModel.class);

	public static final String DEFAULT_IMAGE_EDIT_MODEL = ImageModel.DALL_E_2.getValue();

	/**
	 * The default options used for the image completion requests.
	 */
	private final OpenAiImageEditOptions defaultOptions;

	/**
	 * The retry template used to retry the OpenAI Image API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the OpenAI Image API.
	 */
	private final OpenAiImageApi openAiImageApi;

	/**
	 * Creates an instance of the OpenAiImageEditModel.
	 * @param openAiImageApi The OpenAiImageApi instance to be used for interacting with
	 * the OpenAI Image API.
	 * @throws IllegalArgumentException if openAiImageApi is null
	 */
	public OpenAiImageEditModel(OpenAiImageApi openAiImageApi) {
		this(openAiImageApi, OpenAiImageEditOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiImageEditModel.
	 * @param openAiImageApi The OpenAiImageApi instance to be used for interacting with
	 * the OpenAI Image API.
	 * @param options The OpenAiImageEditOptions to configure the image model.
	 * @param retryTemplate The retry template.
	 */
	public OpenAiImageEditModel(OpenAiImageApi openAiImageApi, OpenAiImageEditOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(openAiImageApi, "OpenAiImageApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		this.openAiImageApi = openAiImageApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ImageResponse call(ImageEditPrompt ImageEditPrompt) {
		ImageEditPrompt requestImageEditPrompt = buildRequestImageEditPrompt(ImageEditPrompt);
		OpenAiImageEditRequest imageRequest = createRequest(requestImageEditPrompt);

		ResponseEntity<OpenAiImageResponse> imageResponseEntity = this.retryTemplate
			.execute(ctx -> this.openAiImageApi.createImageEdit(imageRequest));
		return convertResponse(imageResponseEntity, imageRequest);
	}

	private OpenAiImageEditRequest createRequest(ImageEditPrompt imageEditPrompt) {
		List<byte[]> images = imageEditPrompt.getInstructions().getImageResource().stream().map(this::toBytes).toList();
		String prompt = imageEditPrompt.getInstructions().getPrompt();
		OpenAiImageEditOptions imageOptions = (OpenAiImageEditOptions) imageEditPrompt.getOptions();

		OpenAiImageEditRequest imageRequest = new OpenAiImageEditRequest(images, prompt, DEFAULT_IMAGE_EDIT_MODEL);

		return ModelOptionsUtils.merge(imageOptions, imageRequest, OpenAiImageEditRequest.class);
	}

	private ImageResponse convertResponse(ResponseEntity<OpenAiImageResponse> imageResponseEntity,
			OpenAiImageEditRequest OpenAiImageEditRequest) {
		OpenAiImageResponse imageApiResponse = imageResponseEntity.getBody();
		if (imageApiResponse == null) {
			logger.warn("No image response returned for request: {}", OpenAiImageEditRequest);
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

	private ImageEditPrompt buildRequestImageEditPrompt(ImageEditPrompt ImageEditPrompt) {
		// Process runtime options
		OpenAiImageEditOptions runtimeOptions = null;
		if (ImageEditPrompt.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(ImageEditPrompt.getOptions(), ImageEditOptions.class,
					OpenAiImageEditOptions.class);
		}

		OpenAiImageEditOptions requestOptions = runtimeOptions == null ? this.defaultOptions : OpenAiImageEditOptions
			.builder()
			// Handle portable image options
			.model(ModelOptionsUtils.mergeOption(runtimeOptions.getModel(), this.defaultOptions.getModel()))
			.mask(ModelOptionsUtils.mergeOption(runtimeOptions.getMask(), this.defaultOptions.getMask()))
			.N(ModelOptionsUtils.mergeOption(runtimeOptions.getN(), this.defaultOptions.getN()))
			.responseFormat(ModelOptionsUtils.mergeOption(runtimeOptions.getResponseFormat(),
					this.defaultOptions.getResponseFormat()))
			// Handle OpenAI specific image options
			.quality(ModelOptionsUtils.mergeOption(runtimeOptions.getQuality(), this.defaultOptions.getQuality()))
			.user(ModelOptionsUtils.mergeOption(runtimeOptions.getUser(), this.defaultOptions.getUser()))
			.build();

		return new ImageEditPrompt(ImageEditPrompt.getInstructions(), requestOptions);
	}

	private byte[] toBytes(Resource resource) {
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

}
