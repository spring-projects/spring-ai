/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.retry.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an OpenAI API error response structure. This class is used to parse
 * structured error responses from OpenAI API to provide more user-friendly error
 * messages.
 *
 * @author snowykte0426
 * @since 1.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiErrorResponse {

	@JsonProperty("error")
	private ErrorDetail error;

	public OpenAiErrorResponse() {
	}

	public OpenAiErrorResponse(ErrorDetail error) {
		this.error = error;
	}

	public ErrorDetail getError() {
		return this.error;
	}

	public void setError(ErrorDetail error) {
		this.error = error;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ErrorDetail {

		@JsonProperty("message")
		private String message;

		@JsonProperty("type")
		private String type;

		@JsonProperty("param")
		private String param;

		@JsonProperty("code")
		private String code;

		public ErrorDetail() {
		}

		public ErrorDetail(String message, String type, String param, String code) {
			this.message = message;
			this.type = type;
			this.param = param;
			this.code = code;
		}

		public String getMessage() {
			return this.message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getType() {
			return this.type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getParam() {
			return this.param;
		}

		public void setParam(String param) {
			this.param = param;
		}

		public String getCode() {
			return this.code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		@Override
		public String toString() {
			return "ErrorDetail{" + "message='" + this.message + '\'' + ", type='" + this.type + '\'' + ", param='"
					+ this.param + '\'' + ", code='" + this.code + '\'' + '}';
		}

	}

	@Override
	public String toString() {
		return "OpenAiErrorResponse{" + "error=" + this.error + '}';
	}

}
