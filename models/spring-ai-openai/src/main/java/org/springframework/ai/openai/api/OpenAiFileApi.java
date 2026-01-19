/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.openai.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * OpenAI File API.
 *
 * @author Sun Yuhan
 * @see <a href= "https://platform.openai.com/docs/api-reference/files">Files API</a>
 */
public class OpenAiFileApi {

	private final RestClient restClient;

	/**
	 * Create a new OpenAI file api.
	 * @param baseUrl api base URL.
	 * @param apiKey OpenAI apiKey.
	 * @param headers the http headers to use.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public OpenAiFileApi(String baseUrl, ApiKey apiKey, HttpHeaders headers, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> authHeaders = h -> h.addAll(headers);

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(authHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.defaultRequest(requestHeadersSpec -> {
				if (!(apiKey instanceof NoopApiKey)) {
					requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.getValue());
				}
			})
			.build();
	}

	/**
	 * Create a new OpenAI file api.
	 * @param restClient RestClient instance.
	 */
	public OpenAiFileApi(RestClient restClient) {
		this.restClient = restClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Upload a file that can be used across various endpoints
	 * @param uploadFileRequest The request body
	 * @return Response entity containing the file object
	 */
	public ResponseEntity<FileObject> uploadFile(UploadFileRequest uploadFileRequest) {
		MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
		multipartBody.add("file", new ByteArrayResource(uploadFileRequest.file()) {
			@Override
			public String getFilename() {
				return uploadFileRequest.fileName();
			}
		});
		multipartBody.add("purpose", uploadFileRequest.purpose());

		return this.restClient.post().uri("/v1/files").body(multipartBody).retrieve().toEntity(FileObject.class);
	}

	/**
	 * Returns a list of files
	 * @param listFileRequest The request body
	 * @return Response entity containing the files
	 */
	public ResponseEntity<FileObjectResponse> listFiles(ListFileRequest listFileRequest) {
		return this.restClient.get().uri(uriBuilder -> {
			UriBuilder builder = uriBuilder.path("/v1/files");
			if (null != listFileRequest.after()) {
				builder = builder.queryParam("after", listFileRequest.after());
			}
			if (null != listFileRequest.limit()) {
				builder = builder.queryParam("limit", listFileRequest.limit());
			}
			if (null != listFileRequest.order()) {
				builder = builder.queryParam("order", listFileRequest.order());
			}
			if (null != listFileRequest.purpose()) {
				builder = builder.queryParam("purpose", listFileRequest.purpose());
			}
			return builder.build();
		}).retrieve().toEntity(FileObjectResponse.class);
	}

	/**
	 * Returns information about a specific file
	 * @param fileId The file id
	 * @return Response entity containing the file object
	 */
	public ResponseEntity<FileObject> retrieveFile(String fileId) {
		return this.restClient.get().uri("/v1/files/%s".formatted(fileId)).retrieve().toEntity(FileObject.class);
	}

	/**
	 * Delete a file
	 * @param fileId The file id
	 * @return Response entity containing the deletion status
	 */
	public ResponseEntity<DeleteFileResponse> deleteFile(String fileId) {
		return this.restClient.delete()
			.uri("/v1/files/%s".formatted(fileId))
			.retrieve()
			.toEntity(DeleteFileResponse.class);
	}

	/**
	 * Returns the contents of the specified file
	 * @param fileId The file id
	 * @return Response entity containing the file content
	 */
	public ResponseEntity<String> retrieveFileContent(String fileId) {
		return this.restClient.get().uri("/v1/files/%s/content".formatted(fileId)).retrieve().toEntity(String.class);
	}

	/**
	 * The intended purpose of the uploaded file
	 */
	public enum Purpose {

		// @formatter:off
		/**
		 * Used in the Assistants API
		 */
		@JsonProperty("assistants")
		ASSISTANTS("assistants"),
		/**
		 * Used in the Batch API
		 */
		@JsonProperty("batch")
		BATCH("batch"),
		/**
		 * Used for fine-tuning
		 */
		@JsonProperty("fine-tune")
		FINE_TUNE("fine-tune"),
		/**
		 * Images used for vision fine-tuning
		 */
		@JsonProperty("vision")
		VISION("vision"),
		/**
		 * Flexible file type for any purpose
		 */
		@JsonProperty("user_data")
		USER_DATA("user_data"),
		/**
		 * Used for eval data sets
		 */
		@JsonProperty("evals")
		EVALS("evals");
		// @formatter:on

		private final String value;

		Purpose(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record UploadFileRequest(
	// @formatter:off
		@JsonProperty("file") byte[] file,
		@JsonProperty("fileName") String fileName,
		@JsonProperty("purpose") String purpose) {
		// @formatter:on

		public static Builder builder() {
			return new Builder();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof UploadFileRequest that)) {
				return false;
			}
			return Arrays.equals(this.file, that.file) && Objects.equals(this.fileName, that.fileName)
					&& Objects.equals(this.purpose, that.purpose);
		}

		@Override
		public int hashCode() {
			int result = Arrays.hashCode(this.file);
			result = 31 * result + Objects.hashCode(this.fileName);
			result = 31 * result + Objects.hashCode(this.purpose);
			return result;
		}

		@Override
		public String toString() {
			return "UploadFileRequest{file=" + Arrays.toString(this.file) + ", fileName="
					+ Objects.toString(this.fileName) + ", purpose=" + Objects.toString(this.purpose) + "}";
		}

		public static final class Builder {

			private byte[] file;

			private String fileName;

			private String purpose;

			public Builder file(byte[] file) {
				this.file = file;
				return this;
			}

			public Builder fileName(String fileName) {
				this.fileName = fileName;
				return this;
			}

			public Builder purpose(String purpose) {
				this.purpose = purpose;
				return this;
			}

			public Builder purpose(Purpose purpose) {
				this.purpose = purpose.getValue();
				return this;
			}

			public UploadFileRequest build() {
				Assert.notNull(this.file, "file must not be empty");
				Assert.notNull(this.fileName, "fileName must not be empty");
				Assert.notNull(this.purpose, "purpose must not be empty");

				return new UploadFileRequest(this.file, this.fileName, this.purpose);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ListFileRequest(
	// @formatter:off
		@JsonProperty("after") String after,
		@JsonProperty("limit") Integer limit,
		@JsonProperty("order") String order,
		@JsonProperty("purpose") String purpose) {
		// @formatter:on

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private String after;

			private Integer limit;

			private String order;

			private String purpose;

			public Builder after(String after) {
				this.after = after;
				return this;
			}

			public Builder limit(Integer limit) {
				this.limit = limit;
				return this;
			}

			public Builder order(String order) {
				this.order = order;
				return this;
			}

			public Builder purpose(String purpose) {
				this.purpose = purpose;
				return this;
			}

			public Builder purpose(Purpose purpose) {
				this.purpose = purpose.getValue();
				return this;
			}

			public ListFileRequest build() {
				return new ListFileRequest(this.after, this.limit, this.order, this.purpose);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FileObject(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("bytes") Integer bytes,
		@JsonProperty("created_at") Integer createdAt,
		@JsonProperty("expires_at") Integer expiresAt,
		@JsonProperty("filename") String filename,
		@JsonProperty("purpose") String purpose) {
		// @formatter:on
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FileObjectResponse(
	// @formatter:off
		@JsonProperty("data") List<FileObject> data,
		@JsonProperty("object") String object
		// @formatter:on
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record DeleteFileResponse(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("deleted") Boolean deleted) {
		// @formatter:on
	}

	public static final class Builder {

		private String baseUrl = OpenAiApiConstants.DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private HttpHeaders headers = new HttpHeaders();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder headers(HttpHeaders headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public OpenAiFileApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new OpenAiFileApi(this.baseUrl, this.apiKey, this.headers, this.restClientBuilder,
					this.responseErrorHandler);
		}

	}

}
