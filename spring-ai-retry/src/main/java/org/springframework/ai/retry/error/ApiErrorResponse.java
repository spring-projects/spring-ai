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

package org.springframework.ai.retry.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Parsed shape of the JSON error envelope returned by OpenAI-compatible APIs (DeepSeek,
 * Mistral AI, MiniMax, Ollama, etc.) — used by {@link ApiErrorMessageImprover} to turn
 * raw error bodies into user-friendly messages.
 *
 * @author snowykte0426
 * @since 1.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ApiErrorResponse {

	@JsonProperty("error")
	private @Nullable ErrorDetail error;

	public ApiErrorResponse() {
	}

	public ApiErrorResponse(final @Nullable ErrorDetail error) {
		this.error = error;
	}

	public @Nullable ErrorDetail getError() {
		return this.error;
	}

	public void setError(final @Nullable ErrorDetail error) {
		this.error = error;
	}

	@Override
	public String toString() {
		return "ApiErrorResponse{" + "error=" + this.error + '}';
	}

	/**
	 * Parsed shape of the nested {@code error} field in an OpenAI-compatible error
	 * response.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static final class ErrorDetail {

		@JsonProperty("message")
		private @Nullable String message;

		@JsonProperty("type")
		private @Nullable String type;

		@JsonProperty("param")
		private @Nullable String param;

		@JsonProperty("code")
		private @Nullable String code;

		public ErrorDetail() {
		}

		public ErrorDetail(final @Nullable String message, final @Nullable String type, final @Nullable String param,
				final @Nullable String code) {
			this.message = message;
			this.type = type;
			this.param = param;
			this.code = code;
		}

		public @Nullable String getMessage() {
			return this.message;
		}

		public void setMessage(final @Nullable String message) {
			this.message = message;
		}

		public @Nullable String getType() {
			return this.type;
		}

		public void setType(final @Nullable String type) {
			this.type = type;
		}

		public @Nullable String getParam() {
			return this.param;
		}

		public void setParam(final @Nullable String param) {
			this.param = param;
		}

		public @Nullable String getCode() {
			return this.code;
		}

		public void setCode(final @Nullable String code) {
			this.code = code;
		}

		@Override
		public String toString() {
			return "ErrorDetail{" + "message='" + this.message + '\'' + ", type='" + this.type + '\'' + ", param='"
					+ this.param + '\'' + ", code='" + this.code + '\'' + '}';
		}

	}

}
