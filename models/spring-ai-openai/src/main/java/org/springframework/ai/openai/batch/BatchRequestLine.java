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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.util.Assert;

/**
 * Represents a single request line in an OpenAI Batch API JSONL input file.
 * <p>
 * Each line follows the format:
 *
 * <pre>
 * {
 *   "custom_id": "entity-123::my-handler",
 *   "method": "POST",
 *   "url": "/v1/chat/completions",
 *   "body": { ... }
 * }
 * </pre>
 *
 * The {@code body} is a generic map that the handler populates with the appropriate API
 * request parameters. This keeps the batch framework endpoint-agnostic.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 * @see <a href="https://platform.openai.com/docs/api-reference/batch">OpenAI Batch
 * API</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchRequestLine(@JsonProperty("custom_id") String customId, @JsonProperty("method") String method,
		@JsonProperty("url") String url, @JsonProperty("body") Map<String, Object> body) {

	public BatchRequestLine {
		Assert.hasText(customId, "customId must not be blank");
		Assert.hasText(method, "method must not be blank");
		Assert.hasText(url, "url must not be blank");
		Assert.notNull(body, "body must not be null");
	}

	/**
	 * Creates a POST request line for the given endpoint.
	 * @param customId the custom ID for tracking this request
	 * @param url the API endpoint URL (e.g., {@code /v1/chat/completions})
	 * @param body the request body parameters
	 * @return a new {@link BatchRequestLine}
	 */
	public static BatchRequestLine post(String customId, String url, Map<String, Object> body) {
		return new BatchRequestLine(customId, "POST", url, body);
	}

}
