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

package org.springframework.ai.chat.client.advisor.tool.index.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.advisor.tool.search.api.ToolIndex;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolReference;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchRequest;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchResponse;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchResponse.SearchMetadata;

/**
 * Lucene-based tool searcher for indexing and searching tool descriptions.
 * <p>
 * This class provides full-text search capabilities for tool metadata using Apache
 * Lucene's in-memory index. Entries are grouped by sessionId and retrieval is filtered by
 * the provided sessionId.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public class LuceneToolIndex implements Closeable, ToolIndex {

	private static final Logger logger = LoggerFactory.getLogger(LuceneToolIndex.class);

	private static final String FIELD_ID = "id";

	private static final String FIELD_SESSION_ID = "sessionId";

	private static final String FIELD_TOOL_NAME = "toolName";

	private static final String FIELD_TOOL_DESCRIPTION = "toolDescription";

	private static final int DEFAULT_MAX_RESULTS = 10;

	private final Analyzer analyzer;

	private final float minScoreThreshold;

	private final AtomicInteger counter = new AtomicInteger(0);

	/**
	 * Map of sessionId to their respective Lucene indexes.
	 */
	private final Map<String, SessionIndex> sessionIndexes = new ConcurrentHashMap<>();

	/**
	 * Creates a new LuceneToolIndex with default settings.
	 */
	public LuceneToolIndex() {
		this(0.25f);
	}

	public LuceneToolIndex(float minScoreThreshold) {
		this.minScoreThreshold = minScoreThreshold;
		this.analyzer = new StandardAnalyzer();
	}

	/**
	 * Gets or creates a SessionIndex for the given sessionId.
	 * @param sessionId the session identifier
	 * @return the SessionIndex for the session
	 */
	private SessionIndex getOrCreateSessionIndex(String sessionId) {
		return this.sessionIndexes.computeIfAbsent(sessionId, key -> {
			try {
				return new SessionIndex(this.analyzer);
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to initialize Lucene index for session: " + sessionId, e);
			}
		});
	}

	@Override
	public void clearIndex(String sessionId) {
		SessionIndex sessionIndex = this.sessionIndexes.remove(sessionId);
		if (sessionIndex != null) {
			try {
				sessionIndex.close();
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to clear the index for session: " + sessionId, e);
			}
		}
	}

	@Override
	public void indexTool(String sessionId, ToolReference toolReference) {
		this.add(sessionId, String.valueOf(this.counter.getAndIncrement()), toolReference.toolName(),
				toolReference.summary());
	}

	@Override
	public void indexTools(String sessionId, List<ToolReference> toolReferences) {
		for (ToolReference ref : toolReferences) {
			this.add(sessionId, String.valueOf(this.counter.getAndIncrement()), ref.toolName(), ref.summary());
		}
	}

	@Override
	public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
		String sessionId = toolSearchRequest.sessionId();
		if (sessionId == null || sessionId.isBlank()) {
			logger.warn("No sessionId provided in ToolSearchRequest, returning empty results");
			return ToolSearchResponse.builder().build();
		}

		SessionIndex sessionIndex = this.sessionIndexes.get(sessionId);
		if (sessionIndex == null) {
			logger.debug("No index found for session: {}", sessionId);
			return ToolSearchResponse.builder().build();
		}

		return this.doSearch(sessionIndex, toolSearchRequest.query(),
				toolSearchRequest.maxResults() != null ? toolSearchRequest.maxResults() : DEFAULT_MAX_RESULTS,
				this.minScoreThreshold);
	}

	/**
	 * Adds a single tool to the index for the specified session.
	 * <p>
	 * <b>Thread-safety note:</b> a narrow write-after-close race exists when
	 * {@link #clearIndex(String)} is called concurrently for the same session from a
	 * different thread. The advisor's built-in flow prevents this via the atomic
	 * fingerprint-check in {@code initializeSession()}. Direct callers must ensure that
	 * no concurrent {@code clearIndex()} runs for the same session; if one does, the
	 * document is silently dropped (the session was cleared anyway).
	 * @param sessionId the session ID associated with the tool
	 * @param id unique identifier for the tool
	 * @param toolName name of the tool
	 * @param toolDescription description of the tool (searchable)
	 */
	public void add(String sessionId, String id, String toolName, String toolDescription) {
		try {
			SessionIndex sessionIndex = getOrCreateSessionIndex(sessionId);
			Document doc = this.createDocument(sessionId, id, toolName, toolDescription);
			sessionIndex.writer.addDocument(doc);
		}
		catch (AlreadyClosedException ex) {
			logger.warn("Skipping add for session '{}': index was concurrently cleared", sessionId);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to add document to index", e);
		}
	}

	/**
	 * Commits all pending changes to all session indexes. Call this after batch additions
	 * for better performance.
	 */
	public void commit() {
		for (Map.Entry<String, SessionIndex> entry : this.sessionIndexes.entrySet()) {
			try {
				SessionIndex sessionIndex = entry.getValue();
				sessionIndex.writer.commit();
				sessionIndex.refreshReader();
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to commit changes to index for session: " + entry.getKey(), e);
			}
		}
	}

	/**
	 * Commits all pending changes to the index for the specified session. Call this after
	 * batch additions for better performance.
	 * @param sessionId the session ID to commit changes for
	 */
	public void commit(String sessionId) {
		SessionIndex sessionIndex = this.sessionIndexes.get(sessionId);
		if (sessionIndex != null) {
			try {
				sessionIndex.writer.commit();
				sessionIndex.refreshReader();
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to commit changes to index for session: " + sessionId, e);
			}
		}
	}

	/**
	 * Searches for tools matching the query string within the specified session's index.
	 * @param sessionIndex the session index to search
	 * @param queryString the search query
	 * @param maxResults maximum number of results to return
	 * @param minScore minimum score threshold for results
	 * @return list of matching documents
	 */
	private ToolSearchResponse doSearch(SessionIndex sessionIndex, String queryString, int maxResults, float minScore) {
		try {
			IndexSearcher searcher = new IndexSearcher(sessionIndex.ensureAndGetReader());

			Query query = buildQuery(queryString);
			if (query == null) {
				return ToolSearchResponse.builder().build();
			}

			TopDocs results = searcher.search(query, maxResults);
			return this.extractToolReferences(queryString, searcher, results, minScore);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to search index", e);
		}
	}

	private ToolSearchResponse extractToolReferences(String query, IndexSearcher searcher, TopDocs results,
			float minScore) throws IOException {

		List<ToolReference> foundToolReferences = new ArrayList<>(results.scoreDocs.length);
		StoredFields storedFields = searcher.storedFields();
		for (ScoreDoc scoreDoc : results.scoreDocs) {
			logger.info("Score: {}, name: {}", scoreDoc.score,
					storedFields.document(scoreDoc.doc).get(FIELD_TOOL_NAME));
			if (scoreDoc.score >= minScore) {
				var doc = storedFields.document(scoreDoc.doc);
				foundToolReferences.add(ToolReference.builder()
					.relevanceScore(scoreDoc.score)
					.toolName(doc.get(FIELD_TOOL_NAME))
					.summary(doc.get(FIELD_TOOL_DESCRIPTION))
					.build());
			}
		}

		return ToolSearchResponse.builder()
			.toolReferences(foundToolReferences)
			.totalMatches(foundToolReferences.size())
			.searchMetadata(SearchMetadata.builder().searchType(this.getClass().getSimpleName()).query(query).build())
			.build();
	}

	/**
	 * Deletes a tool from the index for the specified session by its ID.
	 * @param sessionId the session ID
	 * @param id the tool ID to delete
	 */
	public void delete(String sessionId, String id) {
		SessionIndex sessionIndex = this.sessionIndexes.get(sessionId);
		if (sessionIndex != null) {
			try {
				sessionIndex.writer.deleteDocuments(new Term(FIELD_ID, id));
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to delete document from index", e);
			}
		}
	}

	/**
	 * Returns the number of documents in the index for the specified session.
	 * @param sessionId the session ID
	 * @return document count, or 0 if session not found
	 */
	public int size(String sessionId) {
		SessionIndex sessionIndex = this.sessionIndexes.get(sessionId);
		if (sessionIndex == null) {
			return 0;
		}
		try {
			return sessionIndex.ensureAndGetReader().numDocs();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to get index size for session: " + sessionId, e);
		}
	}

	/**
	 * Returns the total number of documents across all session indexes.
	 * @return total document count
	 */
	public int totalSize() {
		int total = 0;
		for (String sessionId : this.sessionIndexes.keySet()) {
			total += size(sessionId);
		}
		return total;
	}

	@Override
	public void close() throws IOException {
		for (SessionIndex sessionIndex : this.sessionIndexes.values()) {
			sessionIndex.close();
		}
		this.sessionIndexes.clear();
		this.analyzer.close();
	}

	private Document createDocument(String sessionId, String id, String toolName, String toolDescription) {
		Document doc = new Document();
		doc.add(new StringField(FIELD_SESSION_ID, sessionId, Field.Store.YES));
		doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
		doc.add(new TextField(FIELD_TOOL_NAME, toolName, Field.Store.YES));
		doc.add(new TextField(FIELD_TOOL_DESCRIPTION, toolDescription, Field.Store.YES));
		return doc;
	}

	private @Nullable Query buildQuery(String queryString) {
		QueryBuilder builder = new QueryBuilder(this.analyzer);
		BooleanQuery.Builder combined = new BooleanQuery.Builder();

		Query descPhrase = builder.createPhraseQuery(FIELD_TOOL_DESCRIPTION, queryString);
		Query descTerms = builder.createBooleanQuery(FIELD_TOOL_DESCRIPTION, queryString, BooleanClause.Occur.SHOULD);
		if (descPhrase != null) {
			combined.add(descPhrase, BooleanClause.Occur.SHOULD);
		}
		if (descTerms != null) {
			combined.add(descTerms, BooleanClause.Occur.SHOULD);
		}

		// Tool name matches are boosted so direct name lookups rank above description
		// hits.
		Query namePhrase = builder.createPhraseQuery(FIELD_TOOL_NAME, queryString);
		Query nameTerms = builder.createBooleanQuery(FIELD_TOOL_NAME, queryString, BooleanClause.Occur.SHOULD);
		if (namePhrase != null) {
			combined.add(new BoostQuery(namePhrase, 4.0f), BooleanClause.Occur.SHOULD);
		}
		if (nameTerms != null) {
			combined.add(new BoostQuery(nameTerms, 2.0f), BooleanClause.Occur.SHOULD);
		}

		BooleanQuery result = combined.build();
		return result.clauses().isEmpty() ? null : result;
	}

	/**
	 * Holds the Lucene index components for a specific session.
	 */
	private static class SessionIndex {

		final Directory directory;

		final IndexWriter writer;

		@Nullable DirectoryReader reader;

		SessionIndex(Analyzer analyzer) throws IOException {
			this.directory = new ByteBuffersDirectory();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			this.writer = new IndexWriter(this.directory, config);
		}

		synchronized DirectoryReader ensureAndGetReader() throws IOException {
			if (this.reader == null) {
				this.writer.commit();
				this.reader = DirectoryReader.open(this.directory);
			}
			else {
				DirectoryReader newReader = DirectoryReader.openIfChanged(this.reader);
				if (newReader != null) {
					this.reader.close();
					this.reader = newReader;
				}
			}
			return this.reader;
		}

		synchronized void refreshReader() throws IOException {
			if (this.reader != null) {
				DirectoryReader newReader = DirectoryReader.openIfChanged(this.reader);
				if (newReader != null) {
					this.reader.close();
					this.reader = newReader;
				}
			}
		}

		synchronized void close() throws IOException {
			if (this.reader != null) {
				this.reader.close();
			}
			this.writer.close();
			this.directory.close();
		}

	}

}
