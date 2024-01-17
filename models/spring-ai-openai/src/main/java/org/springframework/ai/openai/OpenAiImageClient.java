package org.springframework.ai.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
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
			ImagePrompt promptToUse = createUpdatedPrompt(prompt);
			ImageModelOptions promptToUseOptions = promptToUse.getOptions();
			OpenAiImageApi.OpenAiImageRequest openAiImageRequest = new OpenAiImageApi.OpenAiImageRequest(
					promptToUse.getInstructions(), promptToUseOptions.getModel(), promptToUseOptions.getN(),
					promptToUseOptions.getQuality(), promptToUseOptions.getSize(),
					promptToUseOptions.getResponseFormat(), promptToUseOptions.getStyle(),
					promptToUseOptions.getUser());
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

	private ImagePrompt createUpdatedPrompt(ImagePrompt prompt) {
		ImageModelOptions runtimeImageModelOptions = prompt.getOptions();
		ImageOptionsBuilder imageOptionsBuilder = ImageOptionsBuilder.builder();
		if (runtimeImageModelOptions != null) {
			if (runtimeImageModelOptions.getN() != null) {
				imageOptionsBuilder.withN(runtimeImageModelOptions.getN());
			}
			if (runtimeImageModelOptions.getModel() != null) {
				imageOptionsBuilder.withModel(runtimeImageModelOptions.getModel());
			}
			if (runtimeImageModelOptions.getQuality() != null) {
				imageOptionsBuilder.withQuality(runtimeImageModelOptions.getQuality());
			}
			if (runtimeImageModelOptions.getResponseFormat() != null) {
				imageOptionsBuilder.withResponseFormat(runtimeImageModelOptions.getResponseFormat());
			}
			if (runtimeImageModelOptions.getSize() != null) {
				imageOptionsBuilder.withSize(runtimeImageModelOptions.getSize());
			}
			if (runtimeImageModelOptions.getStyle() != null) {
				imageOptionsBuilder.withStyle(runtimeImageModelOptions.getStyle());
			}
			if (runtimeImageModelOptions.getUser() != null) {
				imageOptionsBuilder.withUser(runtimeImageModelOptions.getUser());
			}
		}
		ImageModelOptions updatedImageModelOptions = imageOptionsBuilder.build();
		return new ImagePrompt(prompt.getInstructions(), updatedImageModelOptions);
	}

}
