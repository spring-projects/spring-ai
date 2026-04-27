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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.MultipartField;
import com.openai.core.http.HttpResponse;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Low-level client for the OpenAI Batch and Files APIs, wrapping the official OpenAI Java
 * SDK.
 * <p>
 * Provides methods for:
 * <ul>
 * <li>Generating and uploading JSONL batch input files</li>
 * <li>Creating, retrieving, listing, and cancelling batch executions</li>
 * <li>Downloading batch output/error files</li>
 * <li>Deleting files from OpenAI storage</li>
 * </ul>
 *
 * @author Yasin Akbas
 * @since 2.0.0
 * @see <a href="https://platform.openai.com/docs/api-reference/batch">OpenAI Batch
 * API</a>
 */
public class OpenAiBatchApi {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiBatchApi.class);

	private final OpenAIClient openAiClient;

	private final ObjectMapper objectMapper;

	public OpenAiBatchApi(OpenAIClient openAiClient, ObjectMapper objectMapper) {
		Assert.notNull(openAiClient, "openAiClient must not be null");
		Assert.notNull(objectMapper, "objectMapper must not be null");
		this.openAiClient = openAiClient;
		this.objectMapper = objectMapper;
	}

	/**
	 * Uploads a JSONL file containing batch request lines and creates a batch execution.
	 * @param requestLines the batch request lines to include
	 * @param endpoint the target API endpoint (e.g., {@code /v1/chat/completions})
	 * @param completionWindow the completion window (e.g., {@code 24h})
	 * @param metadata optional metadata to attach to the batch
	 * @return the created {@link Batch} object
	 */
	public Batch createBatch(List<BatchRequestLine> requestLines, BatchCreateParams.Endpoint endpoint,
			BatchCreateParams.CompletionWindow completionWindow, Map<String, String> metadata) {
		Assert.notEmpty(requestLines, "requestLines must not be empty");

		String jsonl = generateJsonl(requestLines);
		FileObject inputFile = uploadJsonlFile(jsonl);

		logger.debug("Creating batch with {} requests, endpoint={}, inputFileId={}", requestLines.size(),
				endpoint.asString(), inputFile.id());

		BatchCreateParams.Builder createParams = BatchCreateParams.builder()
			.inputFileId(inputFile.id())
			.endpoint(endpoint)
			.completionWindow(completionWindow);

		if (metadata != null && !metadata.isEmpty()) {
			BatchCreateParams.Metadata.Builder metaBuilder = BatchCreateParams.Metadata.builder();
			metadata.forEach((key, value) -> metaBuilder.putAdditionalProperty(key, JsonValue.from(value)));
			createParams.metadata(metaBuilder.build());
		}

		return this.openAiClient.batches().create(createParams.build());
	}

	/**
	 * Retrieves the current status and details of a batch execution.
	 * @param batchId the OpenAI batch ID
	 * @return the {@link Batch} object with current status
	 */
	public Batch retrieveBatch(String batchId) {
		Assert.hasText(batchId, "batchId must not be blank");
		return this.openAiClient.batches().retrieve(batchId);
	}

	/**
	 * Cancels an in-progress batch execution.
	 * @param batchId the OpenAI batch ID to cancel
	 * @return the cancelled {@link Batch} object
	 */
	public Batch cancelBatch(String batchId) {
		Assert.hasText(batchId, "batchId must not be blank");
		return this.openAiClient.batches().cancel(batchId);
	}

	/**
	 * Downloads the content of a file from OpenAI and returns it as a string.
	 * @param fileId the file ID to download
	 * @return the file content as a UTF-8 string
	 */
	public String downloadFileContent(String fileId) {
		Assert.hasText(fileId, "fileId must not be blank");
		HttpResponse response = this.openAiClient.files().content(fileId);
		try (var is = response.body()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			throw new OpenAiBatchException("Failed to download file content for fileId: " + fileId, ex);
		}
	}

	/**
	 * Parses JSONL output content into a list of {@link BatchResponseLine} objects.
	 * @param jsonlContent the JSONL content string
	 * @return parsed response lines
	 */
	public List<BatchResponseLine> parseResponseLines(String jsonlContent) {
		Assert.hasText(jsonlContent, "jsonlContent must not be blank");
		try (var reader = new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(jsonlContent.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
			return reader.lines()
				.filter(line -> !line.isBlank())
				.map(line -> deserialize(line, BatchResponseLine.class))
				.collect(Collectors.toList());
		}
		catch (Exception ex) {
			throw new OpenAiBatchException("Failed to parse batch response JSONL", ex);
		}
	}

	/**
	 * Deletes a file from OpenAI storage.
	 * @param fileId the file ID to delete
	 */
	public void deleteFile(String fileId) {
		Assert.hasText(fileId, "fileId must not be blank");
		this.openAiClient.files().delete(fileId);
		logger.debug("Deleted file: {}", fileId);
	}

	private FileObject uploadJsonlFile(String jsonlContent) {
		byte[] bytes = jsonlContent.getBytes(StandardCharsets.UTF_8);
		MultipartField<java.io.InputStream> fileField = MultipartField.<java.io.InputStream>builder()
			.value(new ByteArrayInputStream(bytes))
			.filename("batch_input.jsonl")
			.contentType("application/jsonl")
			.build();
		FileCreateParams params = FileCreateParams.builder().file(fileField).purpose(FilePurpose.BATCH).build();
		return this.openAiClient.files().create(params);
	}

	private String generateJsonl(List<BatchRequestLine> requestLines) {
		return requestLines.stream().map(line -> serialize(line)).collect(Collectors.joining("\n"));
	}

	private String serialize(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (Exception ex) {
			throw new OpenAiBatchException("Failed to serialize batch request line to JSON", ex);
		}
	}

	private <T> T deserialize(String json, Class<T> type) {
		try {
			return this.objectMapper.readValue(json, type);
		}
		catch (Exception ex) {
			throw new OpenAiBatchException("Failed to deserialize batch response line from JSON: " + json, ex);
		}
	}

}
