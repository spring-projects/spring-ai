/*
 * Copyright 2024-2024 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.*;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.openai.metadata.OpenAiImageResponseMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

public class OpenAiImageClient implements ImageClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private OpenAiImageOptions options;

	private final OpenAiImageApi openAiImageApi;

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApi.OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	public OpenAiImageClient(OpenAiImageApi openAiImageApi) {
		Assert.notNull(openAiImageApi, "OpenAiImageApi must not be null");
		this.openAiImageApi = openAiImageApi;
	}

	public OpenAiImageOptions getOptions() {
		return options;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		return this.retryTemplate.execute(ctx -> {
			ImageOptions runtimeOptions = imagePrompt.getOptions();
			OpenAiImageOptions imageOptionsToUse = updateImageOptions(imagePrompt.getOptions());

			// Merge the runtime options passed via the prompt with the
			// StabilityAiImageClient
			// options configured via Autoconfiguration.
			// Runtime options overwrite StabilityAiImageClient options
			OpenAiImageOptions optionsToUse = ModelOptionsUtils.merge(runtimeOptions, this.options,
					OpenAiImageOptionsImpl.class);

			// Copy the org.springframework.ai.model derived ImagePrompt and ImageOptions
			// data
			// types to the data types used in OpenAiImageApi
			String instructions = imagePrompt.getInstructions().get(0).getText();
			String size;
			if (imageOptionsToUse.getWidth() != null && imageOptionsToUse.getHeight() != null) {
				size = imageOptionsToUse.getWidth() + "x" + imageOptionsToUse.getHeight();
			}
			else {
				size = null;
			}
			OpenAiImageApi.OpenAiImageRequest openAiImageRequest = new OpenAiImageApi.OpenAiImageRequest(instructions,
					imageOptionsToUse.getModel(), imageOptionsToUse.getN(), imageOptionsToUse.getQuality(), size,
					imageOptionsToUse.getResponseFormat(), imageOptionsToUse.getStyle(), imageOptionsToUse.getUser());

			// Make the request
			ResponseEntity<OpenAiImageApi.OpenAiImageResponse> imageResponseEntity = this.openAiImageApi
				.createImage(openAiImageRequest);

			// Convert to org.springframework.ai.model derived ImageResponse data type
			return convertResponse(imageResponseEntity, openAiImageRequest);

		});
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

		ImageResponseMetadata openAiImageResponseMetadata = OpenAiImageResponseMetadata.from(imageApiResponse);
		return new ImageResponse(imageGenerationList, openAiImageResponseMetadata);
	}

	private OpenAiImageOptions updateImageOptions(ImageOptions runtimeImageOptions) {
		OpenAiImageOptionsBuilder openAiImageOptionsBuilder = OpenAiImageOptionsBuilder.builder();
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
		OpenAiImageOptions updatedOpenAiImageOptions = openAiImageOptionsBuilder.build();
		return updatedOpenAiImageOptions;
	}

}
