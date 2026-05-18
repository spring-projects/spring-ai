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

package org.springframework.ai.goodmem;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * HTTP client for the GoodMem API. Handles authentication, URL normalization, and the
 * common request patterns used by the {@link GoodMemTools} tool methods.
 *
 * <p>
 * This client is framework-agnostic and can also be used directly by application code
 * that needs raw access to GoodMem operations.
 *
 * @author Spring AI
 */
public final class GoodMemClient {

	private static final Logger logger = LoggerFactory.getLogger(GoodMemClient.class);

	private static final String CHAT_POSTPROCESSOR = "com.goodmem.retrieval.postprocess.ChatPostProcessorFactory";

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

	private final String baseUrl;

	private final String apiKey;

	private final Duration timeout;

	private final HttpClient httpClient;

	private GoodMemClient(Builder builder) {
		Assert.hasText(builder.baseUrl, "baseUrl cannot be null or empty");
		Assert.hasText(builder.apiKey, "apiKey cannot be null or empty");
		Assert.notNull(builder.timeout, "timeout cannot be null");
		this.baseUrl = stripTrailingSlash(builder.baseUrl);
		this.apiKey = builder.apiKey;
		this.timeout = builder.timeout;
		HttpClient.Builder httpBuilder = HttpClient.newBuilder().connectTimeout(builder.timeout);
		if (!builder.verifySsl) {
			httpBuilder.sslContext(insecureSslContext());
		}
		this.httpClient = httpBuilder.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The normalized base URL.
	 */
	public String getBaseUrl() {
		return this.baseUrl;
	}

	// ----- Space operations -----

	/**
	 * Create a new space, or return the existing one when a space with the same name
	 * already exists. The dedupe-by-name behavior matches the GoodMem reference
	 * integrations: callers can safely re-run agent flows without creating duplicates.
	 * @param name the name of the space
	 * @param embedderId the ID of the embedder model to associate with the space
	 * @param chunkingStrategy one of {@code recursive}, {@code sentence}, or {@code none}
	 * @param chunkSize the maximum chunk size in characters (for recursive or sentence
	 * strategies)
	 * @param chunkOverlap the overlap between consecutive chunks in characters
	 * @return a map containing the created (or reused) space info plus a {@code reused}
	 * flag
	 */
	public Map<String, Object> createSpace(String name, String embedderId, String chunkingStrategy, int chunkSize,
			int chunkOverlap) {
		Assert.hasText(name, "name cannot be null or empty");
		Assert.hasText(embedderId, "embedderId cannot be null or empty");
		Assert.hasText(chunkingStrategy, "chunkingStrategy cannot be null or empty");

		// Check for an existing space with the same name first.
		try {
			List<Map<String, Object>> spaces = listSpaces();
			for (Map<String, Object> space : spaces) {
				if (name.equals(space.get("name"))) {
					Map<String, Object> reused = new LinkedHashMap<>();
					reused.put("success", true);
					reused.put("spaceId", space.get("spaceId"));
					reused.put("name", space.get("name"));
					reused.put("embedderId", embedderId);
					reused.put("message", "Space already exists, reusing existing space");
					reused.put("reused", true);
					return reused;
				}
			}
		}
		catch (GoodMemClientException ex) {
			logger.debug("listSpaces failed during createSpace dedupe; falling through to create: {}", ex.getMessage());
		}

		ObjectNode chunkingConfig = OBJECT_MAPPER.createObjectNode();
		if ("none".equals(chunkingStrategy)) {
			chunkingConfig.set("none", OBJECT_MAPPER.createObjectNode());
		}
		else {
			ObjectNode strategyNode = OBJECT_MAPPER.createObjectNode();
			strategyNode.put("chunkSize", chunkSize);
			strategyNode.put("chunkOverlap", chunkOverlap);
			chunkingConfig.set(chunkingStrategy, strategyNode);
		}

		ObjectNode embedderEntry = OBJECT_MAPPER.createObjectNode();
		embedderEntry.put("embedderId", embedderId);

		ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
		requestBody.put("name", name);
		requestBody.set("spaceEmbedders", OBJECT_MAPPER.createArrayNode().add(embedderEntry));
		requestBody.set("defaultChunkingConfig", chunkingConfig);

		JsonNode body = postJson("/v1/spaces", requestBody);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("spaceId", body.path("spaceId").asString());
		result.put("name", body.path("name").asString());
		result.put("embedderId", embedderId);
		result.put("message", "Space created successfully");
		result.put("reused", false);
		return result;
	}

	/**
	 * List all spaces.
	 * @return a list of space dictionaries
	 */
	public List<Map<String, Object>> listSpaces() {
		JsonNode body = getJson("/v1/spaces", Map.of());
		return readSpaceList(body);
	}

	/**
	 * Fetch a space by ID.
	 * @param spaceId the UUID of the space
	 * @return the space as a map
	 */
	public Map<String, Object> getSpace(String spaceId) {
		Assert.hasText(spaceId, "spaceId cannot be null or empty");
		JsonNode body = getJson("/v1/spaces/" + urlEncode(spaceId), Map.of());
		return jsonNodeToMap(body);
	}

	/**
	 * Update an existing space's name, labels, or access settings. Only fields with
	 * non-{@code null} arguments are sent in the PUT payload; omitted fields keep their
	 * current value on the server. {@code replaceLabelsJson} and {@code mergeLabelsJson}
	 * are mutually exclusive.
	 * @param spaceId the UUID of the space to update
	 * @param name new name for the space, or {@code null} to leave unchanged
	 * @param publicRead whether to allow unauthenticated read access, or {@code null} to
	 * leave unchanged
	 * @param replaceLabelsJson a JSON object string of labels that replace all existing
	 * labels (e.g. {@code {"env":"prod"}}), or {@code null}
	 * @param mergeLabelsJson a JSON object string of labels that merge into existing
	 * labels (e.g. {@code {"team":"ml"}}), or {@code null}
	 * @return the updated space as a map, or a map with {@code success=false} when both
	 * label arguments are provided
	 * @since 2.0.0
	 */
	public Map<String, Object> updateSpace(String spaceId, @Nullable String name, @Nullable Boolean publicRead,
			@Nullable String replaceLabelsJson, @Nullable String mergeLabelsJson) {
		Assert.hasText(spaceId, "spaceId cannot be null or empty");
		if (StringUtils.hasText(replaceLabelsJson) && StringUtils.hasText(mergeLabelsJson)) {
			Map<String, Object> err = new LinkedHashMap<>();
			err.put("success", false);
			err.put("error", "Cannot use both replaceLabelsJson and mergeLabelsJson at the same time.");
			return err;
		}

		ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
		if (name != null) {
			requestBody.put("name", name);
		}
		if (publicRead != null) {
			requestBody.put("publicRead", publicRead);
		}
		if (StringUtils.hasText(replaceLabelsJson)) {
			try {
				requestBody.set("replaceLabels", OBJECT_MAPPER.readTree(replaceLabelsJson));
			}
			catch (RuntimeException ex) {
				throw new GoodMemClientException("Failed to parse replaceLabelsJson: " + ex.getMessage(), ex);
			}
		}
		if (StringUtils.hasText(mergeLabelsJson)) {
			try {
				requestBody.set("mergeLabels", OBJECT_MAPPER.readTree(mergeLabelsJson));
			}
			catch (RuntimeException ex) {
				throw new GoodMemClientException("Failed to parse mergeLabelsJson: " + ex.getMessage(), ex);
			}
		}

		JsonNode body = putJson("/v1/spaces/" + urlEncode(spaceId), requestBody);
		return jsonNodeToMap(body);
	}

	/**
	 * Delete a space by ID.
	 * @param spaceId the UUID of the space
	 * @return a confirmation map
	 */
	public Map<String, Object> deleteSpace(String spaceId) {
		Assert.hasText(spaceId, "spaceId cannot be null or empty");
		delete("/v1/spaces/" + urlEncode(spaceId));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("spaceId", spaceId);
		result.put("message", "Space deleted successfully");
		return result;
	}

	// ----- Memory operations -----

	/**
	 * Create a new memory in a space from text or a file. When both are provided, the
	 * file takes priority. The content type is auto-detected from the file extension.
	 * @param spaceId the UUID of the target space
	 * @param textContent plain text content to store, or {@code null}
	 * @param filePath local file path to upload as memory, or {@code null}
	 * @param metadata optional key/value metadata
	 * @return a map with the created memory info
	 */
	public Map<String, Object> createMemory(String spaceId, @Nullable String textContent, @Nullable String filePath,
			@Nullable Map<String, String> metadata) {
		Assert.hasText(spaceId, "spaceId cannot be null or empty");

		ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
		requestBody.put("spaceId", spaceId);

		String contentType;
		if (StringUtils.hasText(filePath)) {
			Path path = Path.of(filePath);
			if (!Files.exists(path)) {
				throw new GoodMemClientException("File not found: " + filePath);
			}
			contentType = guessContentType(filePath);
			byte[] fileBytes;
			try {
				fileBytes = Files.readAllBytes(path);
			}
			catch (IOException ex) {
				throw new GoodMemClientException("Failed to read file: " + filePath, ex);
			}
			requestBody.put("contentType", contentType);
			if (contentType.startsWith("text/")) {
				requestBody.put("originalContent", new String(fileBytes, StandardCharsets.UTF_8));
			}
			else {
				requestBody.put("originalContentB64", Base64.getEncoder().encodeToString(fileBytes));
			}
		}
		else if (StringUtils.hasText(textContent)) {
			contentType = "text/plain";
			requestBody.put("contentType", contentType);
			requestBody.put("originalContent", textContent);
		}
		else {
			throw new GoodMemClientException("No content provided. Provide either textContent or filePath.");
		}

		if (metadata != null && !metadata.isEmpty()) {
			ObjectNode metadataNode = OBJECT_MAPPER.createObjectNode();
			for (Map.Entry<String, String> entry : metadata.entrySet()) {
				metadataNode.put(entry.getKey(), entry.getValue());
			}
			requestBody.set("metadata", metadataNode);
		}

		JsonNode body = postJson("/v1/memories", requestBody);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("memoryId", body.path("memoryId").asString());
		result.put("spaceId", body.path("spaceId").asString());
		result.put("status", body.path("processingStatus").asString("PENDING"));
		result.put("contentType", contentType);
		result.put("message", "Memory created successfully");
		return result;
	}

	/**
	 * Retrieve memories from one or more spaces via semantic similarity. The result is a
	 * streaming NDJSON response that is parsed into chunks, memory definitions, and (when
	 * an LLM post-processor is configured) an {@code abstractReply}.
	 *
	 * <p>
	 * When {@code waitForIndexing} is enabled and no chunks are returned, this method
	 * polls the endpoint for up to 60 seconds at 5 second intervals to allow newly
	 * created memories to finish chunking and embedding.
	 *
	 * <p>
	 * Setting any of {@code rerankerId}, {@code llmId}, {@code relevanceThreshold},
	 * {@code llmTemperature}, or {@code chronologicalResort} appends a
	 * {@code ChatPostProcessor} stage that reranks, filters, re-sorts, and/or generates
	 * an LLM summary.
	 * @param query natural language search query
	 * @param spaceIds comma-separated list of space UUIDs
	 * @param maxResults maximum number of matching chunks to return; also used as the
	 * post-processor {@code max_results} when one is configured
	 * @param includeMemoryDefinition include full memory metadata alongside matched
	 * chunks
	 * @param waitForIndexing when {@code true} and no results are returned, poll for up
	 * to 60 seconds
	 * @param rerankerId optional reranker UUID
	 * @param llmId optional LLM UUID for {@code abstractReply} generation
	 * @param relevanceThreshold optional minimum relevance score (0..1)
	 * @param llmTemperature optional LLM temperature (0..2)
	 * @param chronologicalResort optional chronological reorder of final results
	 * @return retrieval result map
	 */
	public Map<String, Object> retrieveMemories(String query, String spaceIds, int maxResults,
			boolean includeMemoryDefinition, boolean waitForIndexing, @Nullable String rerankerId,
			@Nullable String llmId, @Nullable Double relevanceThreshold, @Nullable Double llmTemperature,
			@Nullable Boolean chronologicalResort) {
		Assert.hasText(query, "query cannot be null or empty");
		Assert.hasText(spaceIds, "spaceIds cannot be null or empty");

		List<String> trimmedIds = new ArrayList<>();
		for (String sid : spaceIds.split(",")) {
			String trimmed = sid.trim();
			if (!trimmed.isEmpty()) {
				trimmedIds.add(trimmed);
			}
		}
		if (trimmedIds.isEmpty()) {
			throw new GoodMemClientException("At least one valid Space ID is required.");
		}

		ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
		requestBody.put("message", query);
		var spaceKeys = OBJECT_MAPPER.createArrayNode();
		for (String sid : trimmedIds) {
			spaceKeys.add(OBJECT_MAPPER.createObjectNode().put("spaceId", sid));
		}
		requestBody.set("spaceKeys", spaceKeys);
		requestBody.put("requestedSize", maxResults);
		requestBody.put("fetchMemory", includeMemoryDefinition);

		ObjectNode postConfig = OBJECT_MAPPER.createObjectNode();
		if (rerankerId != null) {
			postConfig.put("reranker_id", rerankerId);
		}
		if (llmId != null) {
			postConfig.put("llm_id", llmId);
		}
		if (relevanceThreshold != null) {
			postConfig.put("relevance_threshold", relevanceThreshold);
		}
		if (llmTemperature != null) {
			postConfig.put("llm_temp", llmTemperature);
		}
		if (chronologicalResort != null) {
			postConfig.put("chronological_resort", chronologicalResort);
		}
		if (!postConfig.isEmpty()) {
			postConfig.put("max_results", maxResults);
			ObjectNode postProcessor = OBJECT_MAPPER.createObjectNode();
			postProcessor.put("name", CHAT_POSTPROCESSOR);
			postProcessor.set("config", postConfig);
			requestBody.set("postProcessor", postProcessor);
		}

		long maxWaitMs = 60_000L;
		long pollIntervalMs = 5_000L;
		long start = System.nanoTime();
		Map<String, Object> lastResult;
		while (true) {
			String responseText = postNdjson("/v1/memories:retrieve", requestBody);
			lastResult = parseRetrieveResponse(query, responseText);
			Object resultsObj = lastResult.get("results");
			boolean hasResults = (resultsObj instanceof List<?> resultsList) && !resultsList.isEmpty();
			if (hasResults || !waitForIndexing) {
				return lastResult;
			}
			long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
			if (elapsedMs >= maxWaitMs) {
				lastResult.put("message",
						"No results found after waiting 60 seconds for indexing. Memories may still be processing.");
				return lastResult;
			}
			try {
				Thread.sleep(pollIntervalMs);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new GoodMemClientException("Retrieval polling interrupted", ex);
			}
		}
	}

	/**
	 * Fetch a specific memory by ID.
	 * @param memoryId the UUID of the memory
	 * @param includeContent when {@code true}, also fetch the original document content
	 * @return a map containing the memory metadata and optionally the content
	 */
	public Map<String, Object> getMemory(String memoryId, boolean includeContent) {
		Assert.hasText(memoryId, "memoryId cannot be null or empty");
		JsonNode memoryNode = getJson("/v1/memories/" + urlEncode(memoryId), Map.of());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("memory", jsonNodeToMap(memoryNode));

		if (includeContent) {
			try {
				HttpResponse<String> response = doRequest(HttpRequest
					.newBuilder(URI.create(this.baseUrl + "/v1/memories/" + urlEncode(memoryId) + "/content"))
					.timeout(this.timeout)
					.header("X-API-Key", this.apiKey)
					.header("Accept", "application/json, */*")
					.GET()
					.build());
				int status = response.statusCode();
				if (status >= 400) {
					result.put("contentError",
							"Failed to fetch content: HTTP " + status + " - " + truncate(response.body()));
				}
				else {
					String contentType = response.headers().firstValue("content-type").orElse("");
					if (contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
						JsonNode parsed = OBJECT_MAPPER.readTree(response.body());
						result.put("content", jsonNodeToObject(parsed));
					}
					else {
						result.put("content", response.body());
					}
				}
			}
			catch (GoodMemClientException ex) {
				result.put("contentError", "Failed to fetch content: " + ex.getMessage());
			}
			catch (RuntimeException ex) {
				result.put("contentError", "Failed to fetch content: " + ex.getMessage());
			}
		}

		return result;
	}

	/**
	 * List memories within a space, with optional pagination and filtering.
	 * @param spaceId the UUID of the space
	 * @param maxResults max results per page (clamped server-side), or {@code null}
	 * @param nextToken opaque pagination token from a previous response, or {@code null}
	 * @param statusFilter filter by processing status (PENDING, PROCESSING, COMPLETED,
	 * FAILED), or {@code null}
	 * @param includeContent include the original content
	 * @param filterExpression GoodMem filter expression, or {@code null}
	 * @return a map with the {@code memories} list and {@code nextToken}
	 */
	public Map<String, Object> listMemories(String spaceId, @Nullable Integer maxResults, @Nullable String nextToken,
			@Nullable String statusFilter, boolean includeContent, @Nullable String filterExpression) {
		Assert.hasText(spaceId, "spaceId cannot be null or empty");
		Map<String, String> params = new LinkedHashMap<>();
		if (maxResults != null) {
			params.put("maxResults", maxResults.toString());
		}
		if (nextToken != null) {
			params.put("nextToken", nextToken);
		}
		if (statusFilter != null) {
			params.put("statusFilter", statusFilter);
		}
		if (includeContent) {
			params.put("includeContent", "true");
		}
		if (filterExpression != null) {
			params.put("filter", filterExpression);
		}

		JsonNode body = getJson("/v1/spaces/" + urlEncode(spaceId) + "/memories", params);
		List<Map<String, Object>> memories = new ArrayList<>();
		String responseNextToken = null;
		if (body.isObject()) {
			JsonNode memoriesNode = body.path("memories");
			if (memoriesNode.isArray()) {
				for (JsonNode m : memoriesNode) {
					memories.add(jsonNodeToMap(m));
				}
			}
			JsonNode nextTokenNode = body.get("nextToken");
			if (nextTokenNode != null && !nextTokenNode.isNull()) {
				responseNextToken = nextTokenNode.asString();
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("spaceId", spaceId);
		result.put("memories", memories);
		result.put("totalMemories", memories.size());
		result.put("nextToken", responseNextToken);
		return result;
	}

	/**
	 * Delete a memory by ID.
	 * @param memoryId the UUID of the memory
	 * @return a confirmation map
	 */
	public Map<String, Object> deleteMemory(String memoryId) {
		Assert.hasText(memoryId, "memoryId cannot be null or empty");
		delete("/v1/memories/" + urlEncode(memoryId));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("memoryId", memoryId);
		result.put("message", "Memory deleted successfully");
		return result;
	}

	// ----- Embedder operations -----

	/**
	 * List all available embedder models.
	 * @return a list of embedder maps
	 */
	public List<Map<String, Object>> listEmbedders() {
		JsonNode body = getJson("/v1/embedders", Map.of());
		List<Map<String, Object>> embedders = new ArrayList<>();
		if (body.isArray()) {
			for (JsonNode e : body) {
				embedders.add(jsonNodeToMap(e));
			}
		}
		else if (body.isObject() && body.path("embedders").isArray()) {
			for (JsonNode e : body.path("embedders")) {
				embedders.add(jsonNodeToMap(e));
			}
		}
		return embedders;
	}

	// ----- HTTP helpers -----

	private JsonNode getJson(String path, Map<String, String> queryParams) {
		String url = this.baseUrl + path + buildQuery(queryParams);
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(this.timeout)
			.header("X-API-Key", this.apiKey)
			.header("Accept", "application/json")
			.GET()
			.build();
		return parseJson(doRequest(request));
	}

	private JsonNode postJson(String path, JsonNode body) {
		String url = this.baseUrl + path;
		String json;
		try {
			json = OBJECT_MAPPER.writeValueAsString(body);
		}
		catch (RuntimeException ex) {
			throw new GoodMemClientException("Failed to serialize request body", ex);
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(this.timeout)
			.header("X-API-Key", this.apiKey)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
			.build();
		return parseJson(doRequest(request));
	}

	private String postNdjson(String path, JsonNode body) {
		String url = this.baseUrl + path;
		String json;
		try {
			json = OBJECT_MAPPER.writeValueAsString(body);
		}
		catch (RuntimeException ex) {
			throw new GoodMemClientException("Failed to serialize request body", ex);
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(this.timeout)
			.header("X-API-Key", this.apiKey)
			.header("Content-Type", "application/json")
			.header("Accept", "application/x-ndjson")
			.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
			.build();
		HttpResponse<String> response = doRequest(request);
		int status = response.statusCode();
		if (status >= 400) {
			throw new GoodMemClientException(
					"GoodMem retrieve request failed: HTTP " + status + " - " + truncate(response.body()), status,
					response.body());
		}
		return response.body();
	}

	private JsonNode putJson(String path, JsonNode body) {
		String url = this.baseUrl + path;
		String json;
		try {
			json = OBJECT_MAPPER.writeValueAsString(body);
		}
		catch (RuntimeException ex) {
			throw new GoodMemClientException("Failed to serialize request body", ex);
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(this.timeout)
			.header("X-API-Key", this.apiKey)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
			.build();
		return parseJson(doRequest(request));
	}

	private void delete(String path) {
		String url = this.baseUrl + path;
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(this.timeout)
			.header("X-API-Key", this.apiKey)
			.header("Accept", "application/json")
			.DELETE()
			.build();
		HttpResponse<String> response = doRequest(request);
		int status = response.statusCode();
		if (status >= 400) {
			throw new GoodMemClientException(
					"GoodMem DELETE " + path + " failed: HTTP " + status + " - " + truncate(response.body()), status,
					response.body());
		}
	}

	private HttpResponse<String> doRequest(HttpRequest request) {
		try {
			return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new GoodMemClientException(
					"Network failure calling " + request.method() + " " + request.uri() + ": " + ex.getMessage(), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new GoodMemClientException("Request interrupted: " + request.uri(), ex);
		}
	}

	private JsonNode parseJson(HttpResponse<String> response) {
		int status = response.statusCode();
		String body = response.body() != null ? response.body() : "";
		if (status >= 400) {
			throw new GoodMemClientException("GoodMem API request failed: HTTP " + status + " - " + truncate(body),
					status, body);
		}
		try {
			return OBJECT_MAPPER.readTree(body);
		}
		catch (RuntimeException ex) {
			throw new GoodMemClientException("Failed to parse GoodMem response as JSON: " + truncate(body), status,
					body, ex);
		}
	}

	private Map<String, Object> parseRetrieveResponse(String query, String responseText) {
		List<Map<String, Object>> results = new ArrayList<>();
		List<Map<String, Object>> memories = new ArrayList<>();
		String resultSetId = "";
		Map<String, Object> abstractReply = null;

		for (String rawLine : responseText.split("\n")) {
			String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("data:")) {
				line = line.substring("data:".length()).trim();
			}
			if (line.isEmpty() || line.startsWith("event:")) {
				continue;
			}
			JsonNode item;
			try {
				item = OBJECT_MAPPER.readTree(line);
			}
			catch (RuntimeException ex) {
				continue;
			}
			if (item.has("resultSetBoundary")) {
				resultSetId = item.path("resultSetBoundary").path("resultSetId").asString();
			}
			else if (item.has("memoryDefinition")) {
				memories.add(jsonNodeToMap(item.path("memoryDefinition")));
			}
			else if (item.has("abstractReply")) {
				abstractReply = jsonNodeToMap(item.path("abstractReply"));
			}
			else if (item.has("retrievedItem")) {
				JsonNode retrieved = item.path("retrievedItem");
				JsonNode chunkData = retrieved.path("chunk");
				JsonNode chunk = chunkData.path("chunk");
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("chunkId", asNullableString(chunk.path("chunkId")));
				entry.put("chunkText", asNullableString(chunk.path("chunkText")));
				entry.put("memoryId", asNullableString(chunk.path("memoryId")));
				entry.put("relevanceScore", asNullableNumber(chunkData.path("relevanceScore")));
				entry.put("memoryIndex", asNullableNumber(chunkData.path("memoryIndex")));
				results.add(entry);
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("resultSetId", resultSetId);
		result.put("results", results);
		result.put("memories", memories);
		result.put("totalResults", results.size());
		result.put("query", query);
		if (abstractReply != null) {
			result.put("abstractReply", abstractReply);
		}
		return result;
	}

	private static List<Map<String, Object>> readSpaceList(JsonNode body) {
		List<Map<String, Object>> spaces = new ArrayList<>();
		if (body.isArray()) {
			for (JsonNode space : body) {
				spaces.add(jsonNodeToMap(space));
			}
		}
		else if (body.isObject() && body.path("spaces").isArray()) {
			for (JsonNode space : body.path("spaces")) {
				spaces.add(jsonNodeToMap(space));
			}
		}
		return spaces;
	}

	private static Map<String, Object> jsonNodeToMap(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return Map.of();
		}
		Object converted = jsonNodeToObject(node);
		if (converted instanceof Map<?, ?> mapped) {
			Map<String, Object> coerced = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : mapped.entrySet()) {
				coerced.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return coerced;
		}
		return Map.of();
	}

	private static @Nullable Object jsonNodeToObject(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		if (node.isTextual()) {
			return node.asString();
		}
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		if (node.isInt() || node.isLong() || node.isShort()) {
			return node.asLong();
		}
		if (node.isFloat() || node.isDouble() || node.isFloatingPointNumber()) {
			return node.asDouble();
		}
		if (node.isNumber()) {
			return node.numberValue();
		}
		if (node.isArray()) {
			List<Object> list = new ArrayList<>();
			for (JsonNode item : node) {
				list.add(jsonNodeToObject(item));
			}
			return list;
		}
		if (node.isObject()) {
			Map<String, Object> map = new LinkedHashMap<>();
			node.propertyNames().forEach(name -> map.put(name, jsonNodeToObject(node.get(name))));
			return map;
		}
		return node.asString();
	}

	private static @Nullable String asNullableString(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		return node.asString();
	}

	private static @Nullable Number asNullableNumber(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode() || !node.isNumber()) {
			return null;
		}
		return node.numberValue();
	}

	private static String stripTrailingSlash(String url) {
		String trimmed = url.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String buildQuery(Map<String, String> params) {
		if (params == null || params.isEmpty()) {
			return "";
		}
		String encoded = params.entrySet()
			.stream()
			.map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
			.collect(Collectors.joining("&"));
		return "?" + encoded;
	}

	private static String guessContentType(String filePath) {
		String lower = filePath.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (lower.endsWith(".txt") || lower.endsWith(".log")) {
			return "text/plain";
		}
		if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
			return "text/markdown";
		}
		if (lower.endsWith(".html") || lower.endsWith(".htm")) {
			return "text/html";
		}
		if (lower.endsWith(".json")) {
			return "application/json";
		}
		if (lower.endsWith(".csv")) {
			return "text/csv";
		}
		if (lower.endsWith(".docx")) {
			return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		}
		if (lower.endsWith(".doc")) {
			return "application/msword";
		}
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		try {
			String probed = Files.probeContentType(Path.of(filePath));
			if (probed != null) {
				return probed;
			}
		}
		catch (IOException ignored) {
			// Fall through.
		}
		return "application/octet-stream";
	}

	private static String truncate(@Nullable String value) {
		if (value == null) {
			return "";
		}
		String stripped = value.strip();
		return stripped.length() > 500 ? stripped.substring(0, 500) + "..." : stripped;
	}

	private static SSLContext insecureSslContext() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			} };
			context.init(null, trustAll, new SecureRandom());
			return context;
		}
		catch (RuntimeException | java.security.GeneralSecurityException ex) {
			throw new GoodMemClientException("Failed to create insecure SSLContext", ex);
		}
	}

	/**
	 * Builder for {@link GoodMemClient}.
	 */
	public static final class Builder {

		private String baseUrl = "";

		private String apiKey = "";

		private Duration timeout = Duration.ofSeconds(30);

		private boolean verifySsl = true;

		private Builder() {
		}

		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder verifySsl(boolean verifySsl) {
			this.verifySsl = verifySsl;
			return this;
		}

		public GoodMemClient build() {
			return new GoodMemClient(this);
		}

	}

}
