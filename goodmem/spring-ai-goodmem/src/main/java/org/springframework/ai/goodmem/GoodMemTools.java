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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * Spring AI tool surface for the GoodMem memory layer. Each method is annotated with
 * {@link Tool} so it can be registered with a Spring AI {@code ChatClient} or wrapped in
 * a {@code MethodToolCallbackProvider} and exposed to any Spring AI agent.
 *
 * <p>
 * All tool methods catch {@link GoodMemClientException} and return a clean result map
 * containing {@code success=false} and a human-readable {@code error} field, so that AI
 * models receive an actionable message instead of a stack trace.
 *
 * <p>
 * Typical usage: <pre>{@code
 * GoodMemClient client = GoodMemClient.builder()
 *     .baseUrl("https://localhost:8080")
 *     .apiKey(System.getenv("GOODMEM_API_KEY"))
 *     .verifySsl(false)
 *     .build();
 *
 * GoodMemTools tools = new GoodMemTools(client);
 *
 * String reply = ChatClient.builder(chatModel)
 *     .build()
 *     .prompt()
 *     .user("Save this note ...")
 *     .tools(tools)
 *     .call()
 *     .content();
 * }</pre>
 *
 * @author Spring AI
 */
public class GoodMemTools {

	private static final Logger logger = LoggerFactory.getLogger(GoodMemTools.class);

	private static final int DEFAULT_CHUNK_SIZE = 512;

	private static final int DEFAULT_CHUNK_OVERLAP = 50;

	private final GoodMemClient client;

	public GoodMemTools(GoodMemClient client) {
		Assert.notNull(client, "client cannot be null");
		this.client = client;
	}

	/**
	 * The underlying client, exposed so that integrations can access raw GoodMem
	 * operations not surfaced as tools.
	 */
	public GoodMemClient getClient() {
		return this.client;
	}

	@Tool(name = "goodmem_create_space",
			description = "Create a new GoodMem space or reuse an existing one. A space is a logical container for organizing related memories, configured with an embedder for vector search.")
	public Map<String, Object> createSpace(@ToolParam(description = "A unique name for the space.") String name,
			@ToolParam(
					description = "The ID of the embedder model that converts text into vector representations for similarity search.") String embedderId,
			@ToolParam(required = false,
					description = "The chunking strategy for text processing. One of 'recursive', 'sentence', or 'none'. Defaults to 'recursive'.") @Nullable String chunkingStrategy,
			@ToolParam(required = false,
					description = "Maximum chunk size in characters (for recursive/sentence strategies). Defaults to 512.") @Nullable Integer chunkSize,
			@ToolParam(required = false,
					description = "Overlap between consecutive chunks in characters. Defaults to 50.") @Nullable Integer chunkOverlap) {
		String strategy = (chunkingStrategy != null && !chunkingStrategy.isBlank()) ? chunkingStrategy : "recursive";
		int size = (chunkSize != null) ? chunkSize : DEFAULT_CHUNK_SIZE;
		int overlap = (chunkOverlap != null) ? chunkOverlap : DEFAULT_CHUNK_OVERLAP;
		try {
			return this.client.createSpace(name, embedderId, strategy, size, overlap);
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_create_space failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_list_spaces",
			description = "List all GoodMem spaces. Returns each space with its ID, name, embedder configuration, and access settings.")
	public Map<String, Object> listSpaces() {
		try {
			List<Map<String, Object>> spaces = this.client.listSpaces();
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("spaces", spaces);
			result.put("totalSpaces", spaces.size());
			return result;
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_list_spaces failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_update_space",
			description = "Update an existing GoodMem space. Omitted arguments keep their current values. replaceLabelsJson and mergeLabelsJson cannot be used together.")
	public Map<String, Object> updateSpace(@ToolParam(description = "The UUID of the space to update.") String spaceId,
			@ToolParam(required = false, description = "New name for the space.") @Nullable String name,
			@ToolParam(required = false,
					description = "Whether to allow unauthenticated read access to the space.") @Nullable Boolean publicRead,
			@ToolParam(required = false,
					description = "A JSON object of labels that replaces the entire existing label set, e.g. {\"env\":\"prod\"}.") @Nullable String replaceLabelsJson,
			@ToolParam(required = false,
					description = "A JSON object of labels to merge into the existing label set, e.g. {\"team\":\"ml\"}.") @Nullable String mergeLabelsJson) {
		try {
			return this.client.updateSpace(spaceId, name, publicRead, replaceLabelsJson, mergeLabelsJson);
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_update_space failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_create_memory",
			description = "Store a document as a new memory in a GoodMem space. Accepts a local file path or plain text. The memory is chunked and embedded asynchronously.")
	public Map<String, Object> createMemory(
			@ToolParam(description = "The UUID of the space to store the memory in.") String spaceId,
			@ToolParam(required = false,
					description = "Plain text content to store as memory. If both filePath and textContent are provided, the file takes priority.") @Nullable String textContent,
			@ToolParam(required = false,
					description = "Local file path to upload as memory (PDF, DOCX, image, etc.). Content type is auto-detected from the file extension.") @Nullable String filePath,
			@ToolParam(required = false,
					description = "Optional key/value metadata as a flat map of strings.") @Nullable Map<String, String> metadata) {
		try {
			return this.client.createMemory(spaceId, textContent, filePath, metadata);
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_create_memory failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_retrieve_memories",
			description = "Perform similarity-based semantic retrieval across one or more GoodMem spaces. Returns matching chunks ranked by relevance, optional memory definitions, and an LLM-generated abstractReply when a reranker or LLM post-processor is configured.")
	public Map<String, Object> retrieveMemories(@ToolParam(
			description = "A natural language query used to find semantically similar memory chunks.") String query,
			@ToolParam(
					description = "One or more space UUIDs to search across, separated by commas (e.g., 'id1,id2').") String spaceIds,
			@ToolParam(required = false,
					description = "Maximum number of matching chunks to return. Defaults to 5.") @Nullable Integer maxResults,
			@ToolParam(required = false,
					description = "Fetch the full memory metadata alongside the matched chunks. Defaults to true.") @Nullable Boolean includeMemoryDefinition,
			@ToolParam(required = false,
					description = "Retry for up to 60 seconds when no results are found. Useful right after creating a memory while indexing is still in progress. Defaults to true.") @Nullable Boolean waitForIndexing,
			@ToolParam(required = false,
					description = "UUID of a reranker model to refine the order of retrieved chunks via direct query/chunk scoring.") @Nullable String rerankerId,
			@ToolParam(required = false,
					description = "UUID of an LLM that will produce a contextual summary (abstractReply) over the retrieved chunks.") @Nullable String llmId,
			@ToolParam(required = false,
					description = "Minimum relevance score (0..1) below which results are dropped. Only applied when a post-processor is configured.") @Nullable Double relevanceThreshold,
			@ToolParam(required = false,
					description = "Creativity setting for LLM generation (0..2). Only used when llmId is also provided.") @Nullable Double llmTemperature,
			@ToolParam(required = false,
					description = "Reorder final results by creation time after reranking and thresholding.") @Nullable Boolean chronologicalResort) {
		int size = (maxResults != null) ? maxResults : 5;
		boolean includeDef = (includeMemoryDefinition != null) ? includeMemoryDefinition : true;
		boolean wait = (waitForIndexing != null) ? waitForIndexing : true;
		try {
			return this.client.retrieveMemories(query, spaceIds, size, includeDef, wait, rerankerId, llmId,
					relevanceThreshold, llmTemperature, chronologicalResort);
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_retrieve_memories failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_get_memory",
			description = "Fetch a specific GoodMem memory by its ID, including metadata, processing status, and optionally the original content.")
	public Map<String, Object> getMemory(@ToolParam(description = "The UUID of the memory to fetch.") String memoryId,
			@ToolParam(required = false,
					description = "Fetch the original document content of the memory in addition to its metadata. Defaults to true.") @Nullable Boolean includeContent) {
		boolean include = (includeContent != null) ? includeContent : true;
		try {
			return this.client.getMemory(memoryId, include);
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_get_memory failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_delete_memory",
			description = "Permanently delete a GoodMem memory and its associated chunks and vector embeddings.")
	public Map<String, Object> deleteMemory(
			@ToolParam(description = "The UUID of the memory to delete.") String memoryId) {
		try {
			return this.client.deleteMemory(memoryId);
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_delete_memory failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	@Tool(name = "goodmem_list_embedders",
			description = "List all available GoodMem embedder models. Use the returned embedder ID when creating a new space.")
	public Map<String, Object> listEmbedders() {
		try {
			List<Map<String, Object>> embedders = this.client.listEmbedders();
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("embedders", embedders);
			result.put("totalEmbedders", embedders.size());
			return result;
		}
		catch (GoodMemClientException ex) {
			logger.warn("goodmem_list_embedders failed: {}", ex.getMessage());
			return errorResult(ex);
		}
	}

	private static Map<String, Object> errorResult(GoodMemClientException ex) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", false);
		result.put("error", ex.getMessage());
		if (ex.getStatusCode() > 0) {
			result.put("statusCode", ex.getStatusCode());
		}
		return result;
	}

}
