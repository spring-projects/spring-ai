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

package org.springframework.ai.openai.batch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.openai.models.batches.Batch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OpenAiBatchModel}.
 *
 * @author Yasin Akbas
 */
class OpenAiBatchModelTests {

	@Test
	void shouldRejectDuplicateHandlerIds() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		TestHandler handler1 = new TestHandler("same-id", "/v1/chat/completions");
		TestHandler handler2 = new TestHandler("same-id", "/v1/embeddings");

		assertThatThrownBy(
				() -> OpenAiBatchModel.builder().batchApi(batchApi).handlers(List.of(handler1, handler2)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Duplicate handler ID");
	}

	@Test
	void shouldAllowMultipleHandlersWithDifferentIds() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		TestHandler handler1 = new TestHandler("chat-handler", "/v1/chat/completions");
		TestHandler handler2 = new TestHandler("embed-handler", "/v1/embeddings");

		OpenAiBatchModel model = OpenAiBatchModel.builder()
			.batchApi(batchApi)
			.handlers(List.of(handler1, handler2))
			.build();

		assertThat(model.getHandlers()).hasSize(2);
	}

	@Test
	void shouldCreateBatchFromPendingItems() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		Batch mockBatch = mock(Batch.class);
		when(mockBatch.id()).thenReturn("batch_abc123");
		when(mockBatch.inputFileId()).thenReturn("file-input");
		when(batchApi.createBatch(anyList(), any(), any(), anyMap())).thenReturn(mockBatch);

		TestHandler handler = new TestHandler("chat", "/v1/chat/completions");
		handler.addPendingItem("entity-1", Map.of("text", "Hello"));
		handler.addPendingItem("entity-2", Map.of("text", "World"));

		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).handlers(List.of(handler)).build();

		List<Batch> batches = model.createBatchExecutions();

		assertThat(batches).hasSize(1);
		assertThat(batches.get(0).id()).isEqualTo("batch_abc123");
	}

	@Test
	void shouldReturnEmptyWhenNoPendingItems() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		TestHandler handler = new TestHandler("chat", "/v1/chat/completions");

		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).handlers(List.of(handler)).build();

		List<Batch> batches = model.createBatchExecutions();

		assertThat(batches).isEmpty();
		verify(batchApi, never()).createBatch(anyList(), any(), any(), anyMap());
	}

	@Test
	void shouldGroupRequestsByEndpoint() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		Batch mockBatch1 = mock(Batch.class);
		Batch mockBatch2 = mock(Batch.class);
		when(mockBatch1.id()).thenReturn("batch_chat");
		when(mockBatch1.inputFileId()).thenReturn("file-chat");
		when(mockBatch2.id()).thenReturn("batch_embed");
		when(mockBatch2.inputFileId()).thenReturn("file-embed");
		when(batchApi.createBatch(anyList(), any(), any(), anyMap())).thenReturn(mockBatch1, mockBatch2);

		TestHandler chatHandler = new TestHandler("chat", "/v1/chat/completions");
		chatHandler.addPendingItem("e1", Map.of("text", "hello"));

		TestHandler embedHandler = new TestHandler("embed", "/v1/embeddings");
		embedHandler.addPendingItem("e2", Map.of("input", "test"));

		OpenAiBatchModel model = OpenAiBatchModel.builder()
			.batchApi(batchApi)
			.handlers(List.of(chatHandler, embedHandler))
			.build();

		List<Batch> batches = model.createBatchExecutions();

		assertThat(batches).hasSize(2);
	}

	@Test
	void shouldNotifyListenersOnBatchCreated() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		Batch mockBatch = mock(Batch.class);
		when(mockBatch.id()).thenReturn("batch_123");
		when(mockBatch.inputFileId()).thenReturn("file-input");
		when(batchApi.createBatch(anyList(), any(), any(), anyMap())).thenReturn(mockBatch);

		TestHandler handler = new TestHandler("chat", "/v1/chat/completions");
		handler.addPendingItem("e1", Map.of("text", "hello"));

		AtomicInteger listenerCalled = new AtomicInteger(0);
		OpenAiBatchListener listener = new OpenAiBatchListener() {
			@Override
			public void onBatchCreated(Batch batch, int requestCount) {
				assertThat(batch.id()).isEqualTo("batch_123");
				assertThat(requestCount).isEqualTo(1);
				listenerCalled.incrementAndGet();
			}
		};

		OpenAiBatchModel model = OpenAiBatchModel.builder()
			.batchApi(batchApi)
			.handlers(List.of(handler))
			.listeners(List.of(listener))
			.build();

		model.createBatchExecutions();

		assertThat(listenerCalled.get()).isEqualTo(1);
	}

	@Test
	void shouldDispatchSuccessToCorrectHandler() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);

		Batch mockBatch = mock(Batch.class);
		when(mockBatch.id()).thenReturn("batch_done");
		when(mockBatch.status()).thenReturn(Batch.Status.COMPLETED);
		when(mockBatch.outputFileId()).thenReturn(Optional.of("file-output-123"));
		when(mockBatch.errorFileId()).thenReturn(Optional.empty());
		when(mockBatch.inputFileId()).thenReturn("file-input-123");
		when(mockBatch.metadata()).thenReturn(Optional.empty());

		String jsonlOutput = """
				{"id":"req1","custom_id":"entity-1::chat","response":{"status_code":200,"body":{"result":"ok"}}}
				""";
		when(batchApi.retrieveBatch("batch_done")).thenReturn(mockBatch);
		when(batchApi.downloadFileContent("file-output-123")).thenReturn(jsonlOutput);
		when(batchApi.parseResponseLines(jsonlOutput)).thenReturn(List.of(new BatchResponseLine("req1",
				"entity-1::chat", new BatchResponseLine.Response(200, "req_x", Map.of("result", "ok")), null)));

		TestHandler chatHandler = new TestHandler("chat", "/v1/chat/completions");

		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).handlers(List.of(chatHandler)).build();

		model.checkBatchExecution("batch_done");

		assertThat(chatHandler.getSuccessCount()).isEqualTo(1);
		assertThat(chatHandler.getLastSuccessEntityId()).isEqualTo("entity-1");
	}

	@Test
	void shouldPassBatchVersionFromMetadataToHandler() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);

		Batch mockBatch = mock(Batch.class);
		when(mockBatch.id()).thenReturn("batch_ver");
		when(mockBatch.status()).thenReturn(Batch.Status.COMPLETED);
		when(mockBatch.outputFileId()).thenReturn(Optional.of("file-out"));
		when(mockBatch.errorFileId()).thenReturn(Optional.empty());
		when(mockBatch.inputFileId()).thenReturn("file-in");

		// Batch metadata contains handler-version=2
		com.openai.models.batches.Batch.Metadata metadata = com.openai.models.batches.Batch.Metadata.builder()
			.putAdditionalProperty("handler-version", com.openai.core.JsonValue.from("2"))
			.build();
		when(mockBatch.metadata()).thenReturn(Optional.of(metadata));

		when(batchApi.retrieveBatch("batch_ver")).thenReturn(mockBatch);
		when(batchApi.downloadFileContent("file-out")).thenReturn("line");
		when(batchApi.parseResponseLines("line")).thenReturn(List.of(new BatchResponseLine("req1", "entity-1::chat",
				new BatchResponseLine.Response(200, "req_x", Map.of("result", "ok")), null)));

		List<Integer> receivedVersions = new ArrayList<>();
		TestHandler handler = new TestHandler("chat", "/v1/chat/completions") {
			@Override
			public void onSuccess(BatchRequestCustomId customId, Map<String, Object> responseBody, int batchVersion) {
				receivedVersions.add(batchVersion);
				super.onSuccess(customId, responseBody, batchVersion);
			}
		};

		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).handlers(List.of(handler)).build();

		model.checkBatchExecution("batch_ver");

		assertThat(handler.getSuccessCount()).isEqualTo(1);
		assertThat(receivedVersions).containsExactly(2);
	}

	@Test
	void shouldSaveBatchExecutionToRepository() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		Batch mockBatch = mock(Batch.class);
		when(mockBatch.id()).thenReturn("batch_repo_test");
		when(mockBatch.inputFileId()).thenReturn("file-input-456");
		when(batchApi.createBatch(anyList(), any(), any(), anyMap())).thenReturn(mockBatch);

		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		TestHandler handler = new TestHandler("chat", "/v1/chat/completions");
		handler.addPendingItem("e1", Map.of("text", "hello"));

		OpenAiBatchModel model = OpenAiBatchModel.builder()
			.batchApi(batchApi)
			.executionRepository(repository)
			.handlers(List.of(handler))
			.build();

		model.createBatchExecutions();

		Optional<BatchExecution> execution = repository.findById("batch_repo_test");
		assertThat(execution).isPresent();
		assertThat(execution.get().getStatus()).isEqualTo(BatchExecutionStatus.SUBMITTED);
		assertThat(execution.get().getRequestCount()).isEqualTo(1);
		assertThat(execution.get().getEndpoint()).isEqualTo("/v1/chat/completions");
	}

	@Test
	void shouldUpdateExecutionStatusOnCheck() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);

		Batch mockBatch = mock(Batch.class);
		when(mockBatch.id()).thenReturn("batch_status");
		when(mockBatch.status()).thenReturn(Batch.Status.IN_PROGRESS);
		when(batchApi.retrieveBatch("batch_status")).thenReturn(mockBatch);

		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		repository.save(new BatchExecution("batch_status", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 5,
				"file-input"));

		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).executionRepository(repository).build();

		model.checkBatchExecution("batch_status");

		assertThat(repository.findById("batch_status").get().getStatus()).isEqualTo(BatchExecutionStatus.IN_PROGRESS);
	}

	@Test
	void shouldCheckAllPendingExecutions() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);

		Batch batch1 = mock(Batch.class);
		when(batch1.id()).thenReturn("batch_1");
		when(batch1.status()).thenReturn(Batch.Status.IN_PROGRESS);
		when(batchApi.retrieveBatch("batch_1")).thenReturn(batch1);

		Batch batch2 = mock(Batch.class);
		when(batch2.id()).thenReturn("batch_2");
		when(batch2.status()).thenReturn(Batch.Status.IN_PROGRESS);
		when(batchApi.retrieveBatch("batch_2")).thenReturn(batch2);

		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		repository
			.save(new BatchExecution("batch_1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 10, "file-1"));
		repository
			.save(new BatchExecution("batch_2", "/v1/embeddings", BatchExecutionStatus.IN_PROGRESS, 20, "file-2"));
		// Terminal execution should NOT be checked
		BatchExecution done = new BatchExecution("batch_3", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 5,
				"file-3");
		done.setStatus(BatchExecutionStatus.RESULTS_PROCESSED);
		repository.save(done);

		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).executionRepository(repository).build();

		List<Batch> results = model.checkAllPendingExecutions();

		assertThat(results).hasSize(2);
	}

	@Test
	void shouldBuildWithDefaultOptions() {
		OpenAiBatchApi batchApi = mock(OpenAiBatchApi.class);
		OpenAiBatchModel model = OpenAiBatchModel.builder().batchApi(batchApi).build();

		assertThat(model.getOptions()).isNotNull();
		assertThat(model.getOptions().getCompletionWindow()).isEqualTo("24h");
		assertThat(model.getOptions().getMaxRequestsPerBatch()).isEqualTo(50_000);
	}

	/**
	 * Simple test handler for unit testing.
	 */
	static class TestHandler implements BatchRequestHandler<Map<String, Object>> {

		private final String handlerId;

		private final String endpoint;

		private final Map<String, Map<String, Object>> pendingItems = new LinkedHashMap<>();

		private final List<String> successEntityIds = new ArrayList<>();

		private final List<String> errorEntityIds = new ArrayList<>();

		TestHandler(String handlerId, String endpoint) {
			this.handlerId = handlerId;
			this.endpoint = endpoint;
		}

		void addPendingItem(String entityId, Map<String, Object> input) {
			this.pendingItems.put(entityId, input);
		}

		int getSuccessCount() {
			return this.successEntityIds.size();
		}

		int getErrorCount() {
			return this.errorEntityIds.size();
		}

		String getLastSuccessEntityId() {
			return this.successEntityIds.isEmpty() ? null : this.successEntityIds.get(this.successEntityIds.size() - 1);
		}

		@Override
		public String getHandlerId() {
			return this.handlerId;
		}

		@Override
		public String getEndpoint() {
			return this.endpoint;
		}

		@Override
		public Map<String, Object> generateRequestBody(Map<String, Object> input) {
			Map<String, Object> body = new LinkedHashMap<>(input);
			body.put("model", "gpt-4o-mini");
			return body;
		}

		@Override
		public int estimateTokenUsage(Map<String, Object> input) {
			return 100;
		}

		@Override
		public void onSuccess(BatchRequestCustomId customId, Map<String, Object> responseBody, int batchVersion) {
			this.successEntityIds.add(customId.entityId());
		}

		@Override
		public void onError(BatchRequestCustomId customId, BatchResponseLine.Error error, int batchVersion) {
			this.errorEntityIds.add(customId.entityId());
		}

		@Override
		public Map<String, Map<String, Object>> getPendingItems(int maxItems) {
			Map<String, Map<String, Object>> result = new LinkedHashMap<>();
			int count = 0;
			for (Map.Entry<String, Map<String, Object>> entry : this.pendingItems.entrySet()) {
				if (count >= maxItems) {
					break;
				}
				result.put(entry.getKey(), entry.getValue());
				count++;
			}
			return result;
		}

	}

}
