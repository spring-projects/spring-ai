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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;

/**
 * OpenAI Batch API model that orchestrates the lifecycle of batch executions.
 * <p>
 * This model provides three main lifecycle phases:
 * <ol>
 * <li><b>Prepare</b> — Collects pending items from registered
 * {@link BatchRequestHandler}s and converts them into {@link BatchRequestLine}s</li>
 * <li><b>Execute</b> — Uploads JSONL files and creates batch executions on the OpenAI
 * API</li>
 * <li><b>Check</b> — Polls batch status, processes output/error files, and dispatches
 * results to handlers</li>
 * </ol>
 *
 * <p>
 * Following the spring-ai pattern, this class uses a builder for construction and can
 * optionally auto-create the OpenAI client from options if one is not explicitly
 * provided.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 * @see BatchRequestHandler
 * @see OpenAiBatchApi
 * @see OpenAiBatchListener
 */
public final class OpenAiBatchModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiBatchModel.class);

	/**
	 * Metadata key for the handler version stored with each batch.
	 */
	static final String METADATA_HANDLER_VERSION = "handler-version";

	private final OpenAiBatchApi batchApi;

	private final OpenAiBatchOptions options;

	private final List<BatchRequestHandler<?>> handlers;

	private final List<OpenAiBatchListener> listeners;

	private final BatchExecutionRepository executionRepository;

	private OpenAiBatchModel(Builder builder) {
		this.options = builder.options != null ? builder.options : OpenAiBatchOptions.builder().build();

		if (builder.batchApi != null) {
			this.batchApi = builder.batchApi;
		}
		else {
			OpenAIClient openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
					() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
							this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
							this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
							this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
							this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
							this.options.getCustomHeaders()));
			this.batchApi = new OpenAiBatchApi(openAiClient, new com.fasterxml.jackson.databind.ObjectMapper());
		}

		this.handlers = builder.handlers != null ? List.copyOf(builder.handlers) : List.of();
		this.listeners = builder.listeners != null ? List.copyOf(builder.listeners) : List.of();
		this.executionRepository = builder.executionRepository != null ? builder.executionRepository
				: new InMemoryBatchExecutionRepository();

		validateHandlers();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Collects pending items from all registered handlers and creates batch executions.
	 * Requests are grouped by endpoint since OpenAI requires each batch to target a
	 * single endpoint.
	 * @return a list of created {@link Batch} objects
	 */
	public List<Batch> createBatchExecutions() {
		List<Batch> createdBatches = new ArrayList<>();

		Map<String, List<BatchRequestLine>> linesByEndpoint = new HashMap<>();

		for (BatchRequestHandler<?> handler : this.handlers) {
			int maxItems = this.options.getMaxRequestsPerBatch();
			Map<String, ?> pendingItems = handler.getPendingItems(maxItems);

			if (pendingItems.isEmpty()) {
				logger.debug("No pending items for handler: {}", handler.getHandlerId());
				continue;
			}

			@SuppressWarnings("unchecked")
			BatchRequestHandler<Object> typedHandler = (BatchRequestHandler<Object>) handler;
			@SuppressWarnings("unchecked")
			Map<String, Object> typedItems = (Map<String, Object>) pendingItems;

			List<BatchRequestLine> requestLines = typedHandler.toRequestLines(typedItems);

			linesByEndpoint.computeIfAbsent(handler.getEndpoint(), k -> new ArrayList<>()).addAll(requestLines);

			logger.info("Prepared {} request lines from handler '{}' for endpoint '{}'", requestLines.size(),
					handler.getHandlerId(), handler.getEndpoint());
		}

		for (Map.Entry<String, List<BatchRequestLine>> entry : linesByEndpoint.entrySet()) {
			String endpoint = entry.getKey();
			List<BatchRequestLine> allLines = entry.getValue();

			if (allLines.isEmpty()) {
				continue;
			}

			// Split into chunks respecting the max requests per batch limit
			for (int i = 0; i < allLines.size(); i += this.options.getMaxRequestsPerBatch()) {
				List<BatchRequestLine> chunk = allLines.subList(i,
						Math.min(i + this.options.getMaxRequestsPerBatch(), allLines.size()));

				try {
					Batch batch = this.batchApi.createBatch(chunk, BatchCreateParams.Endpoint.of(endpoint),
							BatchCreateParams.CompletionWindow.of(this.options.getCompletionWindow()),
							Map.of("spring-ai-version", "2.0.0", METADATA_HANDLER_VERSION,
									String.valueOf(this.options.getHandlerVersion())));

					createdBatches.add(batch);

					BatchExecution execution = new BatchExecution(batch.id(), endpoint, BatchExecutionStatus.SUBMITTED,
							chunk.size(), batch.inputFileId());
					this.executionRepository.save(execution);

					notifyBatchCreated(batch, chunk.size());

					logger.info("Created batch '{}' with {} requests for endpoint '{}'", batch.id(), chunk.size(),
							endpoint);
				}
				catch (Exception ex) {
					logger.error("Failed to create batch for endpoint '{}' with {} requests", endpoint, chunk.size(),
							ex);
				}
			}
		}

		return createdBatches;
	}

	/**
	 * Checks the status of a batch execution and processes results if completed.
	 * @param batchId the OpenAI batch ID to check
	 * @return the current {@link Batch} status
	 */
	public Batch checkBatchExecution(String batchId) {
		Assert.hasText(batchId, "batchId must not be blank");

		Batch batch = this.batchApi.retrieveBatch(batchId);

		Batch.Status status = batch.status();
		logger.debug("Batch '{}' status: {}", batchId, status);

		updateExecutionStatus(batchId, BatchExecutionStatus.fromOpenAiStatus(status));

		if (Batch.Status.COMPLETED.equals(status)) {
			handleCompletedBatch(batch);
			updateExecutionStatus(batchId, BatchExecutionStatus.RESULTS_PROCESSED);
			notifyBatchCompleted(batch);
		}
		else if (Batch.Status.FAILED.equals(status)) {
			int batchVersion = extractBatchVersion(batch);
			List<BatchResponseLine> errorLines = new ArrayList<>();
			handleErrorFile(batch, errorLines, batchVersion);
			notifyBatchFailed(batch);
		}
		else if (Batch.Status.EXPIRED.equals(status)) {
			handleCompletedBatch(batch);
			updateExecutionStatus(batchId, BatchExecutionStatus.RESULTS_PROCESSED);
			notifyBatchExpired(batch);
		}
		else if (Batch.Status.CANCELLED.equals(status)) {
			notifyBatchCancelled(batch);
		}

		return batch;
	}

	/**
	 * Checks all pending (non-terminal) batch executions tracked by the repository. This
	 * is a convenience method for scheduled polling — call it periodically to
	 * automatically process all in-flight batches.
	 * @return a list of checked {@link Batch} objects with their current statuses
	 */
	public List<Batch> checkAllPendingExecutions() {
		List<BatchExecution> pending = this.executionRepository.findPendingExecutions();

		if (pending.isEmpty()) {
			logger.debug("No pending batch executions to check");
			return List.of();
		}

		logger.info("Checking {} pending batch executions", pending.size());
		List<Batch> results = new ArrayList<>();

		for (BatchExecution execution : pending) {
			try {
				Batch batch = checkBatchExecution(execution.getBatchId());
				results.add(batch);
			}
			catch (Exception ex) {
				logger.error("Failed to check batch execution '{}'", execution.getBatchId(), ex);
			}
		}

		return results;
	}

	/**
	 * Cancels a batch execution.
	 * @param batchId the OpenAI batch ID to cancel
	 * @return the cancelled {@link Batch}
	 */
	public Batch cancelBatch(String batchId) {
		Assert.hasText(batchId, "batchId must not be blank");
		return this.batchApi.cancelBatch(batchId);
	}

	/**
	 * Returns the configured options.
	 */
	public OpenAiBatchOptions getOptions() {
		return this.options;
	}

	/**
	 * Returns the registered handlers (unmodifiable).
	 */
	public List<BatchRequestHandler<?>> getHandlers() {
		return this.handlers;
	}

	/**
	 * Returns the batch execution repository.
	 */
	public BatchExecutionRepository getExecutionRepository() {
		return this.executionRepository;
	}

	private void handleCompletedBatch(Batch batch) {
		int batchVersion = extractBatchVersion(batch);
		List<BatchResponseLine> successLines = new ArrayList<>();
		List<BatchResponseLine> errorLines = new ArrayList<>();

		// Process output file
		batch.outputFileId().ifPresent(outputFileId -> {
			try {
				String content = this.batchApi.downloadFileContent(outputFileId);
				List<BatchResponseLine> lines = this.batchApi.parseResponseLines(content);

				for (BatchResponseLine line : lines) {
					if (line.isSuccess()) {
						successLines.add(line);
						dispatchSuccess(line, batchVersion);
					}
					else {
						errorLines.add(line);
						dispatchError(line, batchVersion);
					}
				}

				if (this.options.isDeleteFilesAfterProcessing()) {
					this.batchApi.deleteFile(outputFileId);
				}
			}
			catch (Exception ex) {
				logger.error("Failed to process output file '{}' for batch '{}'", outputFileId, batch.id(), ex);
			}
		});

		// Process error file
		handleErrorFile(batch, errorLines, batchVersion);

		notifyResultsProcessed(batch, successLines, errorLines);
	}

	private void handleErrorFile(Batch batch, List<BatchResponseLine> errorLines, int batchVersion) {
		batch.errorFileId().ifPresent(errorFileId -> {
			try {
				String content = this.batchApi.downloadFileContent(errorFileId);
				List<BatchResponseLine> lines = this.batchApi.parseResponseLines(content);

				for (BatchResponseLine line : lines) {
					errorLines.add(line);
					dispatchError(line, batchVersion);
				}

				if (this.options.isDeleteFilesAfterProcessing()) {
					this.batchApi.deleteFile(errorFileId);
				}
			}
			catch (Exception ex) {
				logger.error("Failed to process error file '{}' for batch '{}'", errorFileId, batch.id(), ex);
			}
		});

		// Clean up input file
		if (this.options.isDeleteFilesAfterProcessing()) {
			try {
				this.batchApi.deleteFile(batch.inputFileId());
			}
			catch (Exception ex) {
				logger.warn("Failed to delete input file '{}' for batch '{}'", batch.inputFileId(), batch.id(), ex);
			}
		}
	}

	private void dispatchSuccess(BatchResponseLine line, int batchVersion) {
		BatchResponseLine.Response response = line.response();
		if (line.customId() == null || response == null || response.body() == null) {
			logger.warn("Skipping success response with missing customId or body: {}", line.id());
			return;
		}
		Map<String, Object> body = response.body();
		try {
			BatchRequestCustomId customId = BatchRequestCustomId.parse(line.customId());
			findHandler(customId.handlerId()).ifPresentOrElse(handler -> {
				try {
					handler.onSuccess(customId, body, batchVersion);
				}
				catch (Exception ex) {
					logger.error("Handler '{}' failed processing success for entity '{}'", customId.handlerId(),
							customId.entityId(), ex);
				}
			}, () -> logger.warn("No handler found for handlerId '{}' from customId '{}'", customId.handlerId(),
					line.customId()));
		}
		catch (IllegalArgumentException ex) {
			logger.warn("Could not parse customId '{}': {}", line.customId(), ex.getMessage());
		}
	}

	private void dispatchError(BatchResponseLine line, int batchVersion) {
		if (line.customId() == null) {
			logger.warn("Skipping error response with missing customId: {}", line.id());
			return;
		}
		try {
			BatchRequestCustomId customId = BatchRequestCustomId.parse(line.customId());
			BatchResponseLine.Error error = extractError(line);

			findHandler(customId.handlerId()).ifPresent(handler -> {
				try {
					handler.onError(customId, error, batchVersion);
				}
				catch (Exception ex) {
					logger.error("Handler '{}' failed processing error for entity '{}'", customId.handlerId(),
							customId.entityId(), ex);
				}
			});
		}
		catch (IllegalArgumentException ex) {
			logger.warn("Could not parse customId '{}': {}", line.customId(), ex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private BatchResponseLine.Error extractError(BatchResponseLine line) {
		if (line.error() != null) {
			return line.error();
		}
		// Extract error details from non-200 response body
		BatchResponseLine.Response response = line.response();
		if (response != null && response.body() != null) {
			Map<String, Object> body = response.body();
			Object errorObj = body.get("error");
			if (errorObj instanceof Map<?, ?> errorMap) {
				Object codeVal = errorMap.get("code");
				Object msgVal = errorMap.get("message");
				String code = codeVal != null ? String.valueOf(codeVal) : "http_" + response.statusCode();
				String message = msgVal != null ? String.valueOf(msgVal) : "Request failed";
				return new BatchResponseLine.Error(code, message);
			}
			return new BatchResponseLine.Error("http_" + response.statusCode(),
					"Request failed with status " + response.statusCode());
		}
		return new BatchResponseLine.Error("unknown", "No error details available");
	}

	private void updateExecutionStatus(String batchId, BatchExecutionStatus status) {
		this.executionRepository.findById(batchId).ifPresent(execution -> {
			execution.setStatus(status);
			this.executionRepository.save(execution);
		});
	}

	private int extractBatchVersion(Batch batch) {
		return batch.metadata().map(metadata -> {
			com.openai.core.JsonValue versionValue = metadata._additionalProperties().get(METADATA_HANDLER_VERSION);
			if (versionValue != null) {
				try {
					String str = versionValue.toString().replace("\"", "");
					return Integer.parseInt(str);
				}
				catch (NumberFormatException ex) {
					logger.warn("Invalid handler-version in batch '{}' metadata: {}", batch.id(), versionValue);
				}
			}
			return OpenAiBatchOptions.DEFAULT_HANDLER_VERSION;
		}).orElse(OpenAiBatchOptions.DEFAULT_HANDLER_VERSION);
	}

	private java.util.Optional<BatchRequestHandler<?>> findHandler(String handlerId) {
		return this.handlers.stream().filter(h -> h.getHandlerId().equals(handlerId)).findFirst();
	}

	private void validateHandlers() {
		Map<String, Integer> handlerIdCounts = new HashMap<>();
		for (BatchRequestHandler<?> handler : this.handlers) {
			String id = handler.getHandlerId();
			Assert.hasText(id, "handler ID must not be blank");
			Assert.isTrue(!id.contains(BatchRequestCustomId.DELIMITER),
					"handler ID must not contain the delimiter '" + BatchRequestCustomId.DELIMITER + "'");
			handlerIdCounts.merge(id, 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> entry : handlerIdCounts.entrySet()) {
			if (entry.getValue() > 1) {
				throw new IllegalArgumentException("Duplicate handler ID: '" + entry.getKey() + "' (found "
						+ entry.getValue() + " handlers with the same ID)");
			}
		}
	}

	private void notifyBatchCreated(Batch batch, int requestCount) {
		for (OpenAiBatchListener listener : this.listeners) {
			try {
				listener.onBatchCreated(batch, requestCount);
			}
			catch (Exception ex) {
				logger.warn("Listener failed on onBatchCreated", ex);
			}
		}
	}

	private void notifyBatchCompleted(Batch batch) {
		for (OpenAiBatchListener listener : this.listeners) {
			try {
				listener.onBatchCompleted(batch);
			}
			catch (Exception ex) {
				logger.warn("Listener failed on onBatchCompleted", ex);
			}
		}
	}

	private void notifyBatchFailed(Batch batch) {
		for (OpenAiBatchListener listener : this.listeners) {
			try {
				listener.onBatchFailed(batch);
			}
			catch (Exception ex) {
				logger.warn("Listener failed on onBatchFailed", ex);
			}
		}
	}

	private void notifyBatchExpired(Batch batch) {
		for (OpenAiBatchListener listener : this.listeners) {
			try {
				listener.onBatchExpired(batch);
			}
			catch (Exception ex) {
				logger.warn("Listener failed on onBatchExpired", ex);
			}
		}
	}

	private void notifyBatchCancelled(Batch batch) {
		for (OpenAiBatchListener listener : this.listeners) {
			try {
				listener.onBatchCancelled(batch);
			}
			catch (Exception ex) {
				logger.warn("Listener failed on onBatchCancelled", ex);
			}
		}
	}

	private void notifyResultsProcessed(Batch batch, List<BatchResponseLine> successLines,
			List<BatchResponseLine> errorLines) {
		for (OpenAiBatchListener listener : this.listeners) {
			try {
				listener.onBatchResultsProcessed(batch, successLines, errorLines);
			}
			catch (Exception ex) {
				logger.warn("Listener failed on onBatchResultsProcessed", ex);
			}
		}
	}

	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiBatchApi batchApi;

		private @Nullable OpenAiBatchOptions options;

		private @Nullable List<BatchRequestHandler<?>> handlers;

		private @Nullable List<OpenAiBatchListener> listeners;

		private @Nullable BatchExecutionRepository executionRepository;

		private Builder() {
		}

		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		public Builder batchApi(OpenAiBatchApi batchApi) {
			this.batchApi = batchApi;
			return this;
		}

		public Builder options(OpenAiBatchOptions options) {
			this.options = options;
			return this;
		}

		public Builder handlers(List<BatchRequestHandler<?>> handlers) {
			this.handlers = handlers;
			return this;
		}

		public Builder listeners(List<OpenAiBatchListener> listeners) {
			this.listeners = listeners;
			return this;
		}

		public Builder executionRepository(BatchExecutionRepository executionRepository) {
			this.executionRepository = executionRepository;
			return this;
		}

		public OpenAiBatchModel build() {
			return new OpenAiBatchModel(this);
		}

	}

}
