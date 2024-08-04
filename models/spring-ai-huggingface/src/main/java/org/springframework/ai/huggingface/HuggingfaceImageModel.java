package org.springframework.ai.huggingface;

import org.springframework.ai.huggingface.api.ImageGenerationInferenceApi;
import org.springframework.ai.huggingface.text2image.HuggingfaceImageOptions;
import org.springframework.ai.huggingface.invoker.ApiClient;
import org.springframework.ai.huggingface.model.imagegen.GenerateParameters;
import org.springframework.ai.huggingface.model.imagegen.GenerateRequest;
import org.springframework.ai.image.*;

import java.util.Base64;
import java.util.List;

/**
 * An implementation of {@link ImageModel} that interfaces with HuggingFace Inference
 * Endpoints for text-to-image generation.
 *
 * @author Denis Lobo
 */
public class HuggingfaceImageModel implements ImageModel {

	/**
	 * Token required for authenticating with the HuggingFace Inference API.
	 */
	private final String apiToken;

	/**
	 * Client for making API calls.
	 */
	private ApiClient apiClient = new ApiClient();

	private ImageGenerationInferenceApi imageGenApi = new ImageGenerationInferenceApi();

	/**
	 * Constructs a new HuggingfaceImageModel with the specified API token and base path.
	 * @param apiToken The API token for HuggingFace.
	 * @param basePath The base path for API requests.
	 */
	public HuggingfaceImageModel(final String apiToken, String basePath) {
		this.apiToken = apiToken;
		this.apiClient.setBasePath(basePath);
		this.apiClient.addDefaultHeader("Authorization", "Bearer " + this.apiToken);
		this.imageGenApi.setApiClient(this.apiClient);
	}

	@Override
	public ImageResponse call(ImagePrompt prompt) {
		final GenerateParameters generateParameters = createGenerateParameters(prompt.getOptions());
		final GenerateRequest generateRequest = createGenerateRequest(prompt.getInstructions(), generateParameters);

		// huggingface eps with text-to-image models return only single image in default
		// mode
		final String base64Encoded = generateImage(generateRequest, prompt);
		final Image image = new Image(null, base64Encoded);
		final ImageGeneration imageGeneration = new ImageGeneration(image);
		return new ImageResponse(List.of(imageGeneration), new ImageResponseMetadata());
	}

	private String generateImage(GenerateRequest generateRequest, ImagePrompt prompt) {
		final String mimeType = prompt.getOptions().getResponseFormat();
		switch (mimeType) {
			case "application/json" -> {
				return new String(this.imageGenApi.generate(generateRequest, prompt.getOptions().getResponseFormat()));
			}
			default -> {
				byte[] bytes = this.imageGenApi.generate(generateRequest, prompt.getOptions().getResponseFormat());
				return Base64.getEncoder().encodeToString(bytes);
			}
		}
	}

	private GenerateRequest createGenerateRequest(List<ImageMessage> promptInstructs,
			GenerateParameters generateParameters) {
		final GenerateRequest request = new GenerateRequest();
		final List<String> instructions = promptInstructs.stream().map(ImageMessage::getText).toList();

		request.setParameters(generateParameters);
		request.setInputs(instructions);
		return request;
	}

	private GenerateParameters createGenerateParameters(ImageOptions options) {
		final GenerateParameters params = new GenerateParameters();
		params.setWidth(options.getWidth());
		params.setHeight(options.getHeight());
		params.setNumImagesPerPrompt(options.getN());

		if (options instanceof HuggingfaceImageOptions hfImageOptions) {
			params.setClipSkip(hfImageOptions.getClipSkip());
			params.setGuidanceScale(hfImageOptions.getGuidanceScale());
			params.setNumInferenceSteps(hfImageOptions.getNumInferenceSteps());
			params.setNegativePrompt(List.of(hfImageOptions.getNegativePrompt()));
		}
		return params;
	}

}
