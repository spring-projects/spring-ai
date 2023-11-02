package org.springframework.ai.ollama.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.prompt.Prompt;
import org.springframework.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A client implementation for interacting with Ollama Service. This class acts as an
 * interface between the application and the Ollama AI Service, handling request creation,
 * communication, and response processing.
 *
 * @author nullptr
 */
public class OllamaClient implements AiClient {

	/** Logger for logging the events and messages. */
	private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

	/** Mapper for JSON serialization and deserialization. */
	private static final ObjectMapper jsonMapper = new ObjectMapper();

	/** HTTP client for making asynchronous calls to the Ollama Service. */
	private static final HttpClient httpClient = HttpClient.newBuilder().build();

	/** Base URL of the Ollama Service. */
	private final String baseUrl;

	/** Name of the model to be used for the AI service. */
	private final String model;

	/** Optional callback to handle individual generation results. */
	private Consumer<OllamaGenerateResult> simpleCallback;

	/**
	 * Constructs an OllamaClient with the specified base URL and model.
	 * @param baseUrl Base URL of the Ollama Service.
	 * @param model Model specification for the AI service.
	 */
	public OllamaClient(String baseUrl, String model) {
		this.baseUrl = baseUrl;
		this.model = model;
	}

	/**
	 * Constructs an OllamaClient with the specified base URL, model, and a callback.
	 * @param baseUrl Base URL of the Ollama Service.
	 * @param model Model specification for the AI service.
	 * @param simpleCallback Callback to handle individual generation results.
	 */
	public OllamaClient(String baseUrl, String model, Consumer<OllamaGenerateResult> simpleCallback) {
		this(baseUrl, model);
		this.simpleCallback = simpleCallback;
	}

	@Override
	public AiResponse generate(Prompt prompt) {
		validatePrompt(prompt);

		HttpRequest request = buildHttpRequest(prompt);
		var response = sendRequest(request);

		List<OllamaGenerateResult> results = readGenerateResults(response.body());
		return getAiResponse(results);
	}

	/**
	 * Validates the provided prompt.
	 * @param prompt The prompt to validate.
	 */
	protected void validatePrompt(Prompt prompt) {
		if (CollectionUtils.isEmpty(prompt.getMessages())) {
			throw new RuntimeException("The prompt message cannot be empty.");
		}

		if (prompt.getMessages().size() > 1) {
			log.warn("Only the first prompt message will be used; subsequent messages will be ignored.");
		}
	}

	/**
	 * Constructs an HTTP request for the provided prompt.
	 * @param prompt The prompt for which the request needs to be built.
	 * @return The constructed HttpRequest.
	 */
	protected HttpRequest buildHttpRequest(Prompt prompt) {
		String requestBody = getGenerateRequestBody(prompt.getMessages().get(0).getContent());

		// remove the suffix '/' if necessary
		String url = !this.baseUrl.endsWith("/") ? this.baseUrl : this.baseUrl.substring(0, this.baseUrl.length() - 1);

		return HttpRequest.newBuilder()
			.uri(URI.create("%s/api/generate".formatted(url)))
			.POST(HttpRequest.BodyPublishers.ofString(requestBody))
			.timeout(Duration.ofMinutes(5L))
			.build();
	}

	/**
	 * Sends the constructed HttpRequest and retrieves the HttpResponse.
	 * @param request The HttpRequest to be sent.
	 * @return HttpResponse containing the response data.
	 */
	protected HttpResponse<InputStream> sendRequest(HttpRequest request) {
		var response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).join();
		if (response.statusCode() != 200) {
			throw new RuntimeException("Ollama call returned an unexpected status: " + response.statusCode());
		}
		return response;
	}

	/**
	 * Serializes the prompt into a request body for the Ollama API call.
	 * @param prompt The prompt to be serialized.
	 * @return Serialized request body as a String.
	 */
	private String getGenerateRequestBody(String prompt) {
		var data = Map.of("model", model, "prompt", prompt);
		try {
			return jsonMapper.writeValueAsString(data);
		}
		catch (JsonProcessingException ex) {
			throw new RuntimeException("Failed to serialize the prompt to JSON", ex);
		}

	}

	/**
	 * Reads and processes the results from the InputStream provided by the Ollama
	 * Service.
	 * @param inputStream InputStream containing the results from the Ollama Service.
	 * @return List of OllamaGenerateResult.
	 */
	protected List<OllamaGenerateResult> readGenerateResults(InputStream inputStream) {
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
			var results = new ArrayList<OllamaGenerateResult>();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				processResponseLine(line, results);
			}
			return results;
		}
		catch (IOException e) {
			throw new RuntimeException("Error parsing Ollama generation response.", e);
		}
	}

	/**
	 * Processes a single line from the Ollama response.
	 * @param line The line to be processed.
	 * @param results List to which parsed results will be added.
	 */
	protected void processResponseLine(String line, List<OllamaGenerateResult> results) {
		if (line.isBlank())
			return;

		log.debug("Received ollama generate response: {}", line);

		OllamaGenerateResult result;
		try {
			result = jsonMapper.readValue(line, OllamaGenerateResult.class);
		}
		catch (IOException e) {
			throw new RuntimeException("Error parsing response line from Ollama.", e);
		}

		if (result.getModel() == null || result.getDone() == null) {
			throw new IllegalStateException("Received invalid data from Ollama.  Model = " + result.getModel()
					+ " , Done = " + result.getDone());

		}

		if (simpleCallback != null) {
			simpleCallback.accept(result);
		}

		results.add(result);
	}

	/**
	 * Converts the list of OllamaGenerateResult into a structured AiResponse.
	 * @param results List of OllamaGenerateResult.
	 * @return Formulated AiResponse.
	 */
	protected AiResponse getAiResponse(List<OllamaGenerateResult> results) {
		var ollamaResponse = results.stream()
			.filter(Objects::nonNull)
			.filter(it -> it.getResponse() != null && !it.getResponse().isBlank())
			.filter(it -> it.getDone() != null)
			.map(OllamaGenerateResult::getResponse)
			.collect(Collectors.joining(""));

		var generation = new Generation(ollamaResponse);

		// TODO investigate mapping of additional metadata/runtime info to the response.
		// Determine if should be top
		// level map vs. nested map
		return new AiResponse(Collections.singletonList(generation), Map.of("ollama-generate-results", results));
	}

	/**
	 * @return Model name for the AI service.
	 */
	public String getModel() {
		return model;
	}

	/**
	 * @return Base URL of the Ollama Service.
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * @return Callback that handles individual generation results.
	 */
	public Consumer<OllamaGenerateResult> getSimpleCallback() {
		return simpleCallback;
	}

	/**
	 * Sets the callback that handles individual generation results.
	 * @param simpleCallback The callback to be set.
	 */
	public void setSimpleCallback(Consumer<OllamaGenerateResult> simpleCallback) {
		this.simpleCallback = simpleCallback;
	}

}
