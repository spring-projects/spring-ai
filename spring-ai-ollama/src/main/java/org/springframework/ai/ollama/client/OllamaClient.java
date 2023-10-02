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
import org.springframework.util.StringUtils;

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
 * Implementation of {@link AiClient} backed by an OllamaService
 *
 * @author nullptr
 */
public class OllamaClient implements AiClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final HttpClient httpClient = HttpClient.newBuilder().build();

    private final String baseUrl;

    private final String model;

    private Consumer<OllamaGenerateResult> simpleCallback;

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public OllamaClient(String baseUrl, String model, Consumer<OllamaGenerateResult> simpleCallback) {
        this(baseUrl, model);
        this.simpleCallback = simpleCallback;
    }

    @Override
    public AiResponse generate(Prompt prompt) {
        if (CollectionUtils.isEmpty(prompt.getMessages())) {
            log.warn("The prompt message cannot empty");
            return null;
        }

        if (prompt.getMessages().size() > 1) {
            log.warn("Only the first prompt message will be used; any subsequent messages will be ignored.");
        }

        HttpRequest request = buildHttpRequest(prompt);
        var response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).join();

        if (response.statusCode() != 200) {
            log.error("Ollama call failed with status code {}.", response.statusCode());
            throw new IllegalStateException("Ollama call failed with invalid status code.");
        }

        List<OllamaGenerateResult> results = readGenerateResults(response.body());
        return getAiResponse(results);
    }

    private HttpRequest buildHttpRequest(Prompt prompt) {
        String requestBody = getGenerateRequestBody(prompt.getMessages().get(0).getContent());

        if (requestBody == null) {
            throw new IllegalArgumentException("Generate request body is null");
        }

        // remove the suffix '/' if necessary
        String baseUrl = !this.baseUrl.endsWith("/") ? this.baseUrl :
                this.baseUrl.substring(0, this.baseUrl.length() - 1);

        return HttpRequest.newBuilder()
                .uri(URI.create("%s/api/generate".formatted(baseUrl)))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5L))
                .build();
    }

    private String getGenerateRequestBody(String prompt) {
        var data = Map.of(
                "model", model,
                "prompt", prompt
        );

        try {
            return jsonMapper.writeValueAsString(data);
        } catch (JsonProcessingException ignored) {
        }

        return null;
    }

    private List<OllamaGenerateResult> readGenerateResults(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        var results = new ArrayList<OllamaGenerateResult>();

        while (true) {
            try {
                var line = bufferedReader.readLine();
                if (line == null) {
                    log.trace("Retrieve the completion status of the Ollama API generation.");
                    break;
                }

                if (!StringUtils.hasText(line)) {
                    // just skipped empty string
                    continue;
                }

                log.trace("Received ollama generate response: {}", line);
                OllamaGenerateResult result = jsonMapper.readValue(line, OllamaGenerateResult.class);

                if (result.getModel() == null || result.getDone() == null) {
                    log.error("No data received from ollama stream: {}", line);
                    throw new IllegalStateException("No data received from ollama stream.");
                }

                if (simpleCallback != null) {
                    simpleCallback.accept(result);
                }

                results.add(result);
            } catch (IOException e) {
                log.error("Error occurred when parsing ollama generate response", e);
                throw new RuntimeException(e);
            }
        }

        return results;
    }

    private AiResponse getAiResponse(List<OllamaGenerateResult> results) {
        var ollamaResponse = results.stream()
                .filter(Objects::nonNull)
                .filter(it -> it.getResponse() != null && !it.getResponse().isBlank())
                .filter(it -> it.getDone() != null)
                .map(OllamaGenerateResult::getResponse)
                .collect(Collectors.joining(""));

        var generation = new Generation(ollamaResponse);

        // TODO investigate mapping of additional metadata/runtime info to the
        return new AiResponse(Collections.singletonList(generation), Map.of("ollama-generate-results", results));
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Consumer<OllamaGenerateResult> getSimpleCallback() {
        return simpleCallback;
    }

    public void setSimpleCallback(Consumer<OllamaGenerateResult> simpleCallback) {
        this.simpleCallback = simpleCallback;
    }
}
