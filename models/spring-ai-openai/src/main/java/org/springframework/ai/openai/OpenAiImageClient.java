package org.springframework.ai.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageOptionsBuilder;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.openai.metadata.OpenAiImageResponseMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

public class OpenAiImageClient implements ImageClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private String model = OpenAiImageApi.DEFAULT_IMAGE_MODEL;

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

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public ImageResponse call(ImagePrompt prompt) {
		return this.retryTemplate.execute(ctx -> {
			OpenAiImageOptions imageOptionsToUse = updateImageOptions(prompt.getOptions());
			OpenAiImageApi.OpenAiImageRequest openAiImageRequest = new OpenAiImageApi.OpenAiImageRequest(
					prompt.getInstructions(), imageOptionsToUse.getModel(), imageOptionsToUse.getN(),
					imageOptionsToUse.getQuality(), imageOptionsToUse.getWidth() + "x" + imageOptionsToUse.getHeight(),
					imageOptionsToUse.getResponseFormat(), imageOptionsToUse.getStyle(), imageOptionsToUse.getUser());
			ResponseEntity<OpenAiImageApi.OpenAiImageResponse> imageResponseEntity = this.openAiImageApi
				.createImage(openAiImageRequest);
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

		});
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
