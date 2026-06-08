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

package org.springframework.ai.tool.toolsearch.index.vectorstore;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse.SearchMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Vector-based tool searcher for semantic search of tool descriptions.
 * <p>
 * This class provides semantic search capabilities using vector embeddings, allowing for
 * more intelligent matching based on meaning rather than just keywords. Uses Spring AI's
 * VectorStore for vector storage.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public class VectorToolIndex implements Closeable, ToolIndex {

	private static final Log logger = LogFactory.getLog(VectorToolIndex.class);

	private static final String METADATA_ID = "id";

	private static final String METADATA_SESSION_ID = "sessionId";

	public static final String METADATA_TOOL_NAME = "toolName";

	public static final String METADATA_TOOL_DESCRIPTION = "toolDescription";

	private static final int DEFAULT_MAX_RESULTS = 10;

	private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.2;

	private final VectorStore vectorStore;

	private final AtomicInteger counter = new AtomicInteger(0);

	private final ConcurrentHashMap<String, List<String>> sessionToolIds = new ConcurrentHashMap<>();

	@Override
	public void clearIndex(String sessionId) {

		List<String> toolIds = this.sessionToolIds.get(sessionId);

		if (toolIds != null) {
			this.vectorStore.delete(toolIds);
			this.sessionToolIds.remove(sessionId);
			if (logger.isInfoEnabled()) {
				logger.info("Cleared " + toolIds.size() + " tools for sessionId=" + sessionId);
			}
		}
		else if (logger.isInfoEnabled()) {
			logger.info("No tools found for sessionId=" + sessionId);
		}
	}

	/**
	 * Creates a new VectorToolIndex with the given vector store.
	 * @param vectorStore the vector store to use for storing and searching tool
	 * embeddings
	 */
	public VectorToolIndex(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public void indexTool(String sessionId, ToolReference toolReference) {
		String id = String.valueOf(this.counter.getAndIncrement());
		this.add(sessionId, id, toolReference.toolName(), toolReference.summary());
		this.sessionToolIds.compute(sessionId, (k, existing) -> {
			List<String> list = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
			list.add(id);
			return list;
		});
	}

	@Override
	public void indexTools(String sessionId, List<ToolReference> toolReferences) {
		if (toolReferences.isEmpty()) {
			return;
		}

		List<String> ids = toolReferences.stream().map(ref -> String.valueOf(this.counter.getAndIncrement())).toList();

		List<Document> documents = new ArrayList<>(toolReferences.size());
		for (int i = 0; i < toolReferences.size(); i++) {
			ToolReference ref = toolReferences.get(i);
			String id = ids.get(i);
			documents.add(new Document(id, ref.summary(), Map.of(METADATA_SESSION_ID, sessionId, METADATA_ID, id,
					METADATA_TOOL_NAME, ref.toolName(), METADATA_TOOL_DESCRIPTION, ref.summary())));
		}

		this.vectorStore.add(documents);
		this.sessionToolIds.compute(sessionId, (k, existing) -> {
			List<String> list = existing != null ? new ArrayList<>(existing) : new ArrayList<>(ids.size());
			list.addAll(ids);
			return list;
		});
		if (logger.isInfoEnabled()) {
			logger.info("Indexed " + documents.size() + " tools for sessionId=" + sessionId);
		}
	}

	@Override
	public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
		int maxResults = toolSearchRequest.maxResults() != null ? toolSearchRequest.maxResults() : DEFAULT_MAX_RESULTS;

		List<Document> docs = this.doSearch(toolSearchRequest.query(), toolSearchRequest.sessionId(), maxResults,
				DEFAULT_SIMILARITY_THRESHOLD);

		List<ToolReference> toolReferences = docs.stream()
			.map(doc -> ToolReference.builder()
				.toolName((String) Objects.requireNonNull(doc.getMetadata().get(METADATA_TOOL_NAME)))
				.relevanceScore(Objects.requireNonNull(doc.getScore()))
				.summary((String) Objects.requireNonNull(doc.getMetadata().get(METADATA_TOOL_DESCRIPTION)))
				.build())
			.toList();

		return ToolSearchResponse.builder()
			.toolReferences(toolReferences)
			.totalMatches(toolReferences.size())
			.searchMetadata(SearchMetadata.builder()
				.searchType(this.getClass().getSimpleName())
				.query(toolSearchRequest.query())
				.build())
			.build();
	}

	/**
	 * Adds a single tool to the vector index.
	 * @param sessionId the session ID associated with the tool
	 * @param id unique identifier for the tool
	 * @param toolName name of the tool
	 * @param toolDescription description of the tool (used for embedding)
	 */
	public void add(String sessionId, String id, String toolName, String toolDescription) {
		Document document = new Document(id, toolDescription, Map.of(METADATA_SESSION_ID, sessionId, METADATA_ID, id,
				METADATA_TOOL_NAME, toolName, METADATA_TOOL_DESCRIPTION, toolDescription));
		this.vectorStore.add(List.of(document));
	}

	/**
	 * Searches for tools matching the query string using semantic similarity.
	 * @param queryString the search query
	 * @return list of matching documents sorted by similarity
	 */
	public List<Document> doSearch(String queryString) {
		return doSearch(queryString, DEFAULT_MAX_RESULTS, DEFAULT_SIMILARITY_THRESHOLD);
	}

	/**
	 * Searches for tools matching the query string with custom parameters.
	 * @param queryString the search query
	 * @param maxResults maximum number of results to return
	 * @param similarityThreshold minimum similarity threshold (0.0 to 1.0)
	 * @return list of matching documents sorted by similarity
	 */
	public List<Document> doSearch(String queryString, int maxResults, double similarityThreshold) {
		return doSearch(queryString, null, maxResults, similarityThreshold);
	}

	/**
	 * Searches the vector store with full control over parameters.
	 * @param queryString the search query
	 * @param sessionId if non-null, restricts results to this session's indexed tools
	 * @param maxResults maximum number of results to return
	 * @param similarityThreshold minimum similarity score (0.0–1.0)
	 * @return matching documents sorted by descending similarity score
	 */
	public List<Document> doSearch(String queryString, @Nullable String sessionId, int maxResults,
			double similarityThreshold) {
		var b = new FilterExpressionBuilder();
		SearchRequest searchRequest = SearchRequest.builder()
			.query(queryString)
			.topK(maxResults)
			.similarityThreshold(similarityThreshold)
			.filterExpression(sessionId != null ? b.eq(METADATA_SESSION_ID, sessionId).build() : null)
			.build();

		return this.vectorStore.similaritySearch(searchRequest);
	}

	/**
	 * Deletes a tool from the index by its ID.
	 * @param id the tool ID to delete
	 */
	public void delete(String id) {
		this.vectorStore.delete(List.of(id));
	}

	/**
	 * Deletes multiple tools from the index.
	 * @param ids list of tool IDs to delete
	 */
	public void deleteAll(List<String> ids) {
		this.vectorStore.delete(ids);
	}

	@Override
	public void close() throws IOException {
		// Vector store lifecycle is managed externally; no cleanup needed here.
	}

	/**
	 * Gets the tool name from a search result document.
	 * @param document the search result document
	 * @return the tool name
	 */
	public static String getToolName(Document document) {
		return (String) Objects.requireNonNull(document.getMetadata().get(METADATA_TOOL_NAME));
	}

	/**
	 * Gets the tool ID from a search result document.
	 * @param document the search result document
	 * @return the tool ID
	 */
	public static String getToolId(Document document) {
		return (String) Objects.requireNonNull(document.getMetadata().get(METADATA_ID));
	}

	/**
	 * Gets the tool description (content) from a search result document.
	 * @param document the search result document
	 * @return the tool description
	 */
	public static String getToolDescription(Document document) {
		return (String) Objects.requireNonNull(document.getMetadata().get(METADATA_TOOL_DESCRIPTION));
	}

}
