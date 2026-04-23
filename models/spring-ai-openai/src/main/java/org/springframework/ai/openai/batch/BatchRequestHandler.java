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

import java.util.List;
import java.util.Map;

/**
 * Interface for batch request handlers that know how to generate OpenAI API request
 * bodies from domain-specific input data.
 * <p>
 * Implementations are responsible for:
 * <ul>
 * <li>Declaring the target API endpoint (e.g., {@code /v1/chat/completions})</li>
 * <li>Providing a unique handler identifier used in {@link BatchRequestCustomId}</li>
 * <li>Generating the request body from domain input data at execution time</li>
 * <li>Processing successful and failed response lines</li>
 * </ul>
 *
 * <p>
 * The handler follows the <b>hybrid storage approach</b>: domain-specific input data is
 * stored in the database, and the full OpenAI API envelope (model, reasoning_effort,
 * response_format, messages, etc.) is generated on demand at execution time. This means
 * configuration changes (e.g., fixing a wrong model or reasoning_effort) automatically
 * apply to all pending requests without data cleanup.
 *
 * @param <I> the type of domain-specific input data
 * @author Yasin Akbas
 * @since 2.0.0
 */
public interface BatchRequestHandler<I> {

	/**
	 * Returns the unique identifier for this handler. Used as the {@code handlerId}
	 * component of {@link BatchRequestCustomId}.
	 * @return the handler identifier, must be non-blank and not contain
	 * {@link BatchRequestCustomId#DELIMITER}
	 */
	String getHandlerId();

	/**
	 * Returns the OpenAI API endpoint this handler targets.
	 * @return the endpoint URL (e.g., {@code /v1/chat/completions},
	 * {@code /v1/embeddings})
	 */
	String getEndpoint();

	/**
	 * Generates the OpenAI API request body from the given domain input data. This method
	 * is called at batch execution time, allowing the request to reflect the latest
	 * handler configuration (model, prompts, parameters, etc.).
	 * @param input the domain-specific input data
	 * @return the request body as a map of parameters suitable for the target endpoint
	 */
	Map<String, Object> generateRequestBody(I input);

	/**
	 * Estimates the number of prompt tokens this request will consume. Used for token
	 * budget management.
	 * @param input the domain-specific input data
	 * @return the estimated token count
	 */
	int estimateTokenUsage(I input);

	/**
	 * Called for each successfully completed response line from the batch. The
	 * {@code batchVersion} is the version stored in the batch metadata at creation time.
	 * Handlers can use this to determine whether the response format matches the current
	 * handler logic.
	 * @param customId the parsed custom ID from the response
	 * @param responseBody the response body from the OpenAI API
	 * @param batchVersion the version from the batch metadata
	 */
	void onSuccess(BatchRequestCustomId customId, Map<String, Object> responseBody, int batchVersion);

	/**
	 * Called for each failed response line from the batch.
	 * @param customId the parsed custom ID from the response
	 * @param error the error details
	 * @param batchVersion the version from the batch metadata
	 */
	void onError(BatchRequestCustomId customId, BatchResponseLine.Error error, int batchVersion);

	/**
	 * Returns the pending domain input items to be processed in the next batch. Each item
	 * is keyed by its entity ID (used as the {@code entityId} in
	 * {@link BatchRequestCustomId}).
	 * @param maxItems the maximum number of items to return
	 * @return a map of entity IDs to domain input data
	 */
	Map<String, I> getPendingItems(int maxItems);

	/**
	 * Converts a list of request lines to be submitted in a batch. This default
	 * implementation generates a {@link BatchRequestLine} for each pending item by
	 * calling {@link #generateRequestBody(Object)}.
	 * @param pendingItems the pending items keyed by entity ID
	 * @return the list of request lines ready for batch submission
	 */
	default List<BatchRequestLine> toRequestLines(Map<String, I> pendingItems) {
		return pendingItems.entrySet().stream().map(entry -> {
			String customId = new BatchRequestCustomId(entry.getKey(), getHandlerId()).toString();
			Map<String, Object> body = generateRequestBody(entry.getValue());
			return BatchRequestLine.post(customId, getEndpoint(), body);
		}).toList();
	}

}
