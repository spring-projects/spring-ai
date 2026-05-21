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

package org.springframework.ai.chat.client.advisor.tool.index.vectorstore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.advisor.tool.search.api.ToolReference;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchRequest;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VectorToolIndex}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class VectorToolIndexTests {

	private static final String SESSION = "session-1";

	@Mock
	private VectorStore vectorStore;

	private VectorToolIndex toolIndex;

	@BeforeEach
	void setUp() {
		this.toolIndex = new VectorToolIndex(this.vectorStore);
	}

	private ToolReference ref(String name, String description) {
		return ToolReference.builder().toolName(name).summary(description).build();
	}

	private ToolSearchResponse search(String query) {
		return this.toolIndex.search(new ToolSearchRequest(SESSION, query, null, null));
	}

	// -------------------------------------------------------------------------
	// searchType
	// -------------------------------------------------------------------------

	@Test
	void searchTypeIsSemantic() {
		assertThat(this.toolIndex.getClass().getSimpleName()).isEqualTo("VectorToolIndex");
	}

	// -------------------------------------------------------------------------
	// indexTool
	// -------------------------------------------------------------------------

	@Test
	void indexToolAddsOneDocumentToVectorStore() {
		this.toolIndex.indexTool(SESSION, ref("weatherTool", "Returns current weather conditions"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).add(captor.capture());

		List<Document> added = captor.getValue();
		assertThat(added).hasSize(1);
		assertThat(added.get(0).getMetadata().get(VectorToolIndex.METADATA_TOOL_NAME)).isEqualTo("weatherTool");
		assertThat(added.get(0).getMetadata().get(VectorToolIndex.METADATA_TOOL_DESCRIPTION))
			.isEqualTo("Returns current weather conditions");
		assertThat(added.get(0).getMetadata().get("sessionId")).isEqualTo(SESSION);
	}

	@Test
	void indexToolTracksIdSoThatClearIndexCanDeleteIt() {
		this.toolIndex.indexTool(SESSION, ref("tool1", "desc1"));
		this.toolIndex.indexTool(SESSION, ref("tool2", "desc2"));

		this.toolIndex.clearIndex(SESSION);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).delete(deleteCaptor.capture());
		assertThat(deleteCaptor.getValue()).hasSize(2);
	}

	// -------------------------------------------------------------------------
	// indexTools (batch)
	// -------------------------------------------------------------------------

	@Test
	void indexToolsBatchAddsAllDocumentsInOneVectorStoreCall() {
		this.toolIndex.indexTools(SESSION,
				List.of(ref("tool1", "First tool"), ref("tool2", "Second tool"), ref("tool3", "Third tool")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore, times(1)).add(captor.capture());

		assertThat(captor.getValue()).hasSize(3);
	}

	@Test
	void indexToolsBatchDocumentsCarryCorrectMetadata() {
		this.toolIndex.indexTools(SESSION, List.of(ref("weatherTool", "Weather forecast service")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).add(captor.capture());

		Document doc = captor.getValue().get(0);
		assertThat(doc.getMetadata().get(VectorToolIndex.METADATA_TOOL_NAME)).isEqualTo("weatherTool");
		assertThat(doc.getMetadata().get(VectorToolIndex.METADATA_TOOL_DESCRIPTION))
			.isEqualTo("Weather forecast service");
		assertThat(doc.getMetadata().get("sessionId")).isEqualTo(SESSION);
		assertThat(doc.getText()).isEqualTo("Weather forecast service");
	}

	@Test
	void indexToolsBatchTracksAllIdsForCleanup() {
		this.toolIndex.indexTools(SESSION, List.of(ref("t1", "d1"), ref("t2", "d2"), ref("t3", "d3")));

		this.toolIndex.clearIndex(SESSION);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).delete(deleteCaptor.capture());
		assertThat(deleteCaptor.getValue()).hasSize(3);
	}

	@Test
	void indexToolsBatchIsNoopForEmptyList() {
		this.toolIndex.indexTools(SESSION, List.of());

		verify(this.vectorStore, never()).add(anyList());
	}

	// -------------------------------------------------------------------------
	// clearIndex
	// -------------------------------------------------------------------------

	@Test
	void clearIndexDeletesAllTrackedIds() {
		this.toolIndex.indexTools(SESSION, List.of(ref("tool1", "desc1"), ref("tool2", "desc2")));

		this.toolIndex.clearIndex(SESSION);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).delete(captor.capture());
		assertThat(captor.getValue()).hasSize(2);
	}

	@Test
	void clearIndexIsNoopForUnknownSession() {
		this.toolIndex.clearIndex("no-such-session");

		verify(this.vectorStore, never()).delete(anyList());
	}

	@Test
	void clearIndexAllowsReindexingAfterwards() {
		this.toolIndex.indexTools(SESSION, List.of(ref("tool1", "desc1")));
		this.toolIndex.clearIndex(SESSION);

		this.toolIndex.indexTools(SESSION, List.of(ref("tool2", "desc2")));

		this.toolIndex.clearIndex(SESSION);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore, times(2)).delete(captor.capture());
		assertThat(captor.getAllValues().get(0)).hasSize(1);
		assertThat(captor.getAllValues().get(1)).hasSize(1);
	}

	// -------------------------------------------------------------------------
	// search
	// -------------------------------------------------------------------------

	@Test
	void searchPassesQueryAndSessionFilterToVectorStore() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		this.toolIndex.search(new ToolSearchRequest(SESSION, "weather forecast", 5, null));

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(this.vectorStore).similaritySearch(captor.capture());

		SearchRequest req = captor.getValue();
		assertThat(req.getQuery()).isEqualTo("weather forecast");
		assertThat(req.getTopK()).isEqualTo(5);
		assertThat(req.getFilterExpression()).isNotNull();
		assertThat(req.getFilterExpression().toString()).contains(SESSION);
	}

	@Test
	void searchMapsDocumentMetadataToToolReferences() {
		Document doc = Document.builder()
			.id("1")
			.text("Provides weather data")
			.metadata(
					Map.of(VectorToolIndex.METADATA_TOOL_NAME, "weatherTool", VectorToolIndex.METADATA_TOOL_DESCRIPTION,
							"Provides weather data", "sessionId", SESSION, "id", "1"))
			.score(0.92)
			.build();
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

		ToolSearchResponse response = search("weather");

		assertThat(response.toolReferences()).hasSize(1);
		assertThat(response.toolReferences().get(0).toolName()).isEqualTo("weatherTool");
		assertThat(response.toolReferences().get(0).summary()).isEqualTo("Provides weather data");
		assertThat(response.toolReferences().get(0).relevanceScore()).isEqualTo(0.92);
	}

	@Test
	void searchReturnsEmptyWhenVectorStoreReturnsNoResults() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		ToolSearchResponse response = search("anything");

		assertThat(response.toolReferences()).isEmpty();
	}

	@Test
	void searchUsesDefaultMaxResultsWhenNotProvided() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		this.toolIndex.search(new ToolSearchRequest(SESSION, "query", null, null));

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(this.vectorStore).similaritySearch(captor.capture());
		assertThat(captor.getValue().getTopK()).isEqualTo(10);
	}

	@Test
	void searchWithMultipleResultsReturnsAllMapped() {
		List<Document> docs = List.of(
				Document.builder()
					.id("1")
					.text("Weather data")
					.metadata(Map.of(VectorToolIndex.METADATA_TOOL_NAME, "weatherTool",
							VectorToolIndex.METADATA_TOOL_DESCRIPTION, "Weather data", "sessionId", SESSION, "id", "1"))
					.score(0.9)
					.build(),
				Document.builder()
					.id("2")
					.text("Calculator")
					.metadata(Map.of(VectorToolIndex.METADATA_TOOL_NAME, "calculatorTool",
							VectorToolIndex.METADATA_TOOL_DESCRIPTION, "Calculator", "sessionId", SESSION, "id", "2"))
					.score(0.7)
					.build());
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);

		ToolSearchResponse response = search("tools");

		assertThat(response.toolReferences()).hasSize(2);
		assertThat(response.toolReferences()).extracting(ToolReference::toolName)
			.containsExactly("weatherTool", "calculatorTool");
		assertThat(response.totalMatches()).isEqualTo(2);
		assertThat(response.searchMetadata().searchType()).isEqualTo("VectorToolIndex");
	}

	// -------------------------------------------------------------------------
	// Session isolation
	// -------------------------------------------------------------------------

	@Test
	void sessionIsolationViaMetadataFilter() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		this.toolIndex.search(new ToolSearchRequest("session-A", "query", null, null));
		this.toolIndex.search(new ToolSearchRequest("session-B", "query", null, null));

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(this.vectorStore, times(2)).similaritySearch(captor.capture());

		List<SearchRequest> requests = captor.getAllValues();
		assertThat(requests.get(0).getFilterExpression().toString()).contains("session-A");
		assertThat(requests.get(1).getFilterExpression().toString()).contains("session-B");
	}

	@Test
	void clearIndexForOneSessionDoesNotAffectAnother() {
		this.toolIndex.indexTools(SESSION, List.of(ref("tool1", "desc1")));
		this.toolIndex.indexTools("session-2", List.of(ref("tool2", "desc2")));

		this.toolIndex.clearIndex(SESSION);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore, times(1)).delete(captor.capture());
		assertThat(captor.getValue()).hasSize(1);
	}

	// -------------------------------------------------------------------------
	// Thread safety
	// -------------------------------------------------------------------------

	@Test
	void concurrentIndexToolsDoesNotCorruptIdList() throws InterruptedException {
		int threadCount = 10;
		int toolsPerThread = 20;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);

		for (int t = 0; t < threadCount; t++) {
			int threadIdx = t;
			executor.submit(() -> {
				try {
					start.await();
					List<ToolReference> refs = new ArrayList<>();
					for (int i = 0; i < toolsPerThread; i++) {
						refs.add(ref("tool-" + threadIdx + "-" + i, "desc " + threadIdx + " " + i));
					}
					this.toolIndex.indexTools(SESSION, refs);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finally {
					done.countDown();
				}
			});
		}

		start.countDown();
		assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();

		// Capture all IDs passed to delete — should be exactly threadCount *
		// toolsPerThread
		this.toolIndex.clearIndex(SESSION);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).delete(captor.capture());
		assertThat(captor.getValue()).hasSize(threadCount * toolsPerThread);
	}

}
