package org.springframework.ai.huggingface.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.huggingface.api.TextGenerationInferenceApi;
import org.springframework.ai.huggingface.invoker.ApiClient;
import org.springframework.ai.huggingface.model.AllOfGenerateResponseDetails;
import org.springframework.ai.huggingface.model.GenerateParameters;
import org.springframework.ai.huggingface.model.GenerateRequest;
import org.springframework.ai.huggingface.model.GenerateResponse;
import org.springframework.ai.prompt.Prompt;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link AiClient} that interfaces with HuggingFace Inference
 * Endpoints for text generation.
 *
 * @author Mark Pollack
 */
public class HuggingfaceAiClient implements AiClient {

	/**
	 * Token required for authenticating with the HuggingFace Inference API.
	 */
	private final String apiToken;

	/**
	 * Client for making API calls.
	 */
	private ApiClient apiClient = new ApiClient();

	/**
	 * Mapper for converting between Java objects and JSON.
	 */
	private ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * API for text generation inferences.
	 */
	private TextGenerationInferenceApi textGenApi = new TextGenerationInferenceApi();

	/**
	 * The maximum number of new tokens to be generated. Note: The total token size for
	 * the Mistral7b instruct model should be less than 1500.
	 */
	private int maxNewTokens = 1000;

	/**
	 * Constructs a new HuggingfaceAiClient with the specified API token and base path.
	 * @param apiToken The API token for HuggingFace.
	 * @param basePath The base path for API requests.
	 */
	public HuggingfaceAiClient(final String apiToken, String basePath) {
		this.apiToken = apiToken;
		this.apiClient.setBasePath(basePath);
		this.apiClient.addDefaultHeader("Authorization", "Bearer " + this.apiToken);
		this.textGenApi.setApiClient(this.apiClient);
	}

	/**
	 * Generate text based on the provided prompt.
	 * @param prompt The input prompt based on which text is to be generated.
	 * @return AiResponse containing the generated text and other related details.
	 */
	@Override
	public AiResponse generate(Prompt prompt) {
		GenerateRequest generateRequest = new GenerateRequest();
		generateRequest.setInputs(prompt.getContents());
		GenerateParameters generateParameters = new GenerateParameters();
		// TODO - need to expose API to set parameters per call.
		generateParameters.setMaxNewTokens(maxNewTokens);
		generateRequest.setParameters(generateParameters);
		GenerateResponse generateResponse = this.textGenApi.generate(generateRequest);
		String generatedText = generateResponse.getGeneratedText();
		List<Generation> generations = new ArrayList<>();
		AllOfGenerateResponseDetails allOfGenerateResponseDetails = generateResponse.getDetails();
		Map<String, Object> detailsMap = objectMapper.convertValue(allOfGenerateResponseDetails,
				new TypeReference<Map<String, Object>>() {
				});
		Generation generation = new Generation(generatedText, detailsMap);
		generations.add(generation);
		return new AiResponse(generations);
	}

	/**
	 * Gets the maximum number of new tokens to be generated.
	 * @return The maximum number of new tokens.
	 */
	public int getMaxNewTokens() {
		return maxNewTokens;
	}

	/**
	 * Sets the maximum number of new tokens to be generated.
	 * @param maxNewTokens The maximum number of new tokens.
	 */
	public void setMaxNewTokens(int maxNewTokens) {
		this.maxNewTokens = maxNewTokens;
	}

}
