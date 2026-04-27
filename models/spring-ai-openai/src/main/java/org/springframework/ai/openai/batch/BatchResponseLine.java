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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Represents a single response line from an OpenAI Batch API JSONL output file.
 * <p>
 * Each line follows the format:
 *
 * <pre>
 * {
 *   "id": "batch_req_abc123",
 *   "custom_id": "entity-123::my-handler",
 *   "response": {
 *     "status_code": 200,
 *     "request_id": "req_abc123",
 *     "body": { ... }
 *   },
 *   "error": {
 *     "code": "...",
 *     "message": "..."
 *   }
 * }
 * </pre>
 *
 * @author Yasin Akbas
 * @since 2.0.0
 * @see <a href="https://platform.openai.com/docs/api-reference/batch">OpenAI Batch
 * API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchResponseLine(@JsonProperty("id") @Nullable String id,
		@JsonProperty("custom_id") @Nullable String customId, @JsonProperty("response") @Nullable Response response,
		@JsonProperty("error") @Nullable Error error) {

	/**
	 * Returns whether this response line indicates a successful response.
	 */
	public boolean isSuccess() {
		return this.response != null && this.response.statusCode() != null && this.response.statusCode() == 200
				&& this.error == null;
	}

	/**
	 * The response envelope containing status code and body.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Response(@JsonProperty("status_code") @Nullable Integer statusCode,
			@JsonProperty("request_id") @Nullable String requestId,
			@JsonProperty("body") @Nullable Map<String, Object> body) {
	}

	/**
	 * Error details when a request in the batch fails.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Error(@JsonProperty("code") @Nullable String code,
			@JsonProperty("message") @Nullable String message) {
	}

}
