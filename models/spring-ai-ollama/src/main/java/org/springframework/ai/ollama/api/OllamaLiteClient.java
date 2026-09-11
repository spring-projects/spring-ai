/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.ollama.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A lightweight, zero-dependency client for interacting directly with a local
 * <a href="https://ollama.ai/">Ollama</a> server via its HTTP API.
 *
 * <p>Unlike {@link OllamaApi}, this client intentionally avoids Spring framework
 * dependencies (WebClient, RestClient, Jackson, etc.) and is designed for users
 * who want the simplest possible way to send a prompt to Ollama and get a raw
 * JSON response back — for example in standalone tools, scripts, or lightweight
 * applications that do not use the full Spring AI stack.
 *
 * <p>This client targets the {@code /api/generate} endpoint and returns the raw
 * JSON response body as a {@link String}, giving callers full control over
 * deserialization.
 *
 * <p>Usage example:
 * <pre>{@code
 * OllamaLiteClient client = new OllamaLiteClient("http://localhost:11434/api/generate");
 * String json = client.generate("llama3", "Why is the sky blue?");
 * }</pre>
 *
 * <p>For production Spring AI applications, prefer {@link OllamaApi} together
 * with {@code OllamaChatModel}, which provide streaming, observability,
 * retry support, and proper Spring Boot auto-configuration.
 *
 * @author Spring AI Contributors
 * @since 1.1.0
 * @see OllamaApi
 */
public final class OllamaLiteClient {

	/** Default Ollama generate endpoint. */
	public static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/generate";

	private final HttpClient httpClient;

	private final String endpoint;

	/**
	 * Creates an {@code OllamaLiteClient} targeting the default local Ollama
	 * endpoint ({@value #DEFAULT_ENDPOINT}).
	 */
	public OllamaLiteClient() {
		this(DEFAULT_ENDPOINT);
	}

	/**
	 * Creates an {@code OllamaLiteClient} targeting the specified Ollama endpoint.
	 * @param endpoint the full URL of the Ollama {@code /api/generate} endpoint;
	 *     must not be {@code null} or blank
	 * @throws IllegalArgumentException if {@code endpoint} is {@code null} or blank
	 */
	public OllamaLiteClient(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			throw new IllegalArgumentException("endpoint must not be null or blank");
		}
		this.endpoint = endpoint;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	/**
	 * Sends a prompt to the Ollama {@code /api/generate} endpoint and returns
	 * the raw JSON response body.
	 *
	 * <p>The request is sent with {@code "stream": false}, so this method blocks
	 * until the model has produced its full response.
	 *
	 * @param model the Ollama model name to use (e.g. {@code "llama3"},
	 *     {@code "mistral"}); must not be {@code null} or blank
	 * @param prompt the prompt text to send; must not be {@code null}
	 * @return the raw JSON response body from the Ollama server
	 * @throws IllegalArgumentException if {@code model} is {@code null} or blank,
	 *     or if {@code prompt} is {@code null}
	 * @throws IOException if an I/O error occurs when sending or receiving
	 * @throws InterruptedException if the operation is interrupted
	 */
	public String generate(String model, String prompt) throws IOException, InterruptedException {
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("model must not be null or blank");
		}
		if (prompt == null) {
			throw new IllegalArgumentException("prompt must not be null");
		}

		String jsonPayload = buildPayload(model, prompt);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(this.endpoint))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
				.build();

		return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
	}

	/**
	 * Returns the Ollama endpoint URL this client is configured to use.
	 * @return the endpoint URL; never {@code null}
	 */
	public String getEndpoint() {
		return this.endpoint;
	}

	/**
	 * Builds the minimal JSON payload for the Ollama {@code /api/generate} endpoint.
	 *
	 * <p>Special JSON characters ({@code "}, {@code \}, newlines, carriage returns,
	 * and tabs) in the prompt are escaped to prevent malformed JSON.
	 * @param model the model name
	 * @param prompt the prompt text
	 * @return a valid JSON string
	 */
	private static String buildPayload(String model, String prompt) {
		String escapedPrompt = prompt
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
		return "{\"model\":\"" + model + "\",\"prompt\":\"" + escapedPrompt + "\",\"stream\":false}";
	}

}
