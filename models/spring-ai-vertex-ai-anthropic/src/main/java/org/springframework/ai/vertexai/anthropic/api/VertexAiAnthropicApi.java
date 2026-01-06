/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.vertexai.anthropic.api;

import java.io.IOException;

import com.google.auth.Credentials;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for Anthropic LLM models hosted on Google Cloud Vertex AI platform.
 * <p>
 * This class extends {@link AnthropicApi} to provide seamless integration with Google
 * Cloud Vertex AI, handling authentication via Google Cloud credentials, Vertex
 * AI-specific request formatting (including version headers), and routing to the
 * appropriate Vertex AI endpoints.
 */
public class VertexAiAnthropicApi extends AnthropicApi {

	private static final String VERTEX_ANTHROPIC_VERSION = "vertex-2023-10-16";

	private static final String LOCATION_GLOBAL = "global";

	private static final String DEFAULT_MODEL = ChatModel.CLAUDE_SONNET_4_5.getValue();

	/**
	 * Constructs a new VertexAiAnthropicApi instance with the specified configuration.
	 * <p>
	 * This constructor is primarily intended for internal use. For standard
	 * instantiation, use {@link #builderForVertexAi()} instead.
	 * @param baseUrl the base URL for the Vertex AI API endpoint
	 * @param completionsPath the path for the chat completions endpoint
	 * @param credentials the Google Cloud credentials for authentication
	 * @param restClientBuilder the builder for creating the RestClient
	 * @param webClientBuilder the builder for creating the WebClient
	 * @param responseErrorHandler the error handler for HTTP responses
	 */
	protected VertexAiAnthropicApi(String baseUrl, String completionsPath, Credentials credentials,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		super(baseUrl, completionsPath, new SimpleApiKey(""), null, restClientBuilder, webClientBuilder,
				responseErrorHandler, null);
		this.credentials = credentials;
	}

	private final Credentials credentials;

	@Override
	public ResponseEntity<ChatCompletionResponse> chatCompletionEntity(ChatCompletionRequest chatRequest,
			HttpHeaders additionalHttpHeader) {
		addAuthHeader(additionalHttpHeader);
		return super.chatCompletionEntity(createVertexCompletionRequest(chatRequest), additionalHttpHeader);
	}

	@Override
	public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest chatRequest,
			HttpHeaders additionalHttpHeader) {
		addAuthHeader(additionalHttpHeader);
		return super.chatCompletionStream(createVertexCompletionRequest(chatRequest), additionalHttpHeader);
	}

	private void addAuthHeader(HttpHeaders headers) {
		try {
			headers.putAll(this.credentials.getRequestMetadata());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ChatCompletionRequest createVertexCompletionRequest(ChatCompletionRequest request) {
		return ChatCompletionRequest.from(request)
			.model((String) null)
			.anthropicVersion(VERTEX_ANTHROPIC_VERSION)
			.build();
	}

	/**
	 * Returns a new builder for constructing {@link VertexAiAnthropicApi} instances.
	 * @return a new {@link Builder} instance
	 */
	public static Builder builderForVertexAi() {
		return new Builder();
	}

	/**
	 * Builder for {@link VertexAiAnthropicApi}.
	 * <p>
	 * Example usage: <pre>{@code
	 * VertexAiAnthropicApi api = VertexAiAnthropicApi.builderForVertexAi()
	 *     .credentials(credentials)
	 *     .projectId("my-project")
	 *     .model(ChatModel.CLAUDE_SONNET_4_5)
	 *     .build();
	 * }</pre>
	 */
	public static final class Builder {

		private Builder() {
		}

		/**
		 * Sets the Google Cloud credentials for authentication with Vertex AI.
		 * @param credentials the credentials to use for API requests
		 * @return this builder instance for method chaining
		 */
		public Builder credentials(Credentials credentials) {
			Assert.notNull(credentials, "credentials cannot be null");
			this.credentials = credentials;
			return this;
		}

		private Credentials credentials;

		/**
		 * Sets the Google Cloud project ID where the Vertex AI resources are located.
		 * @param projectId the project ID
		 * @return this builder instance for method chaining
		 */
		public Builder projectId(String projectId) {
			Assert.notNull(projectId, "projectId cannot be null");
			this.projectId = projectId;
			return this;
		}

		private String projectId;

		/**
		 * Sets the Google Cloud region location for the Vertex AI endpoint (e.g.,
		 * "us-central1"). Defaults to "global" if not specified.
		 * @param location the location string
		 * @return this builder instance for method chaining
		 */
		public Builder location(String location) {
			Assert.notNull(location, "location cannot be null");
			this.location = location;
			return this;
		}

		private String location = LOCATION_GLOBAL;

		/**
		 * Sets the Anthropic model name to use for chat completions. See <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/claude/use-claude#model-list">available
		 * models</a> for the list of supported models.
		 * @param model the model name (e.g., "claude-haiku-4-5@20251001")
		 * @return this builder instance for method chaining
		 */
		public Builder model(String model) {
			Assert.notNull(model, "model cannot be null");
			this.model = model;
			return this;
		}

		/**
		 * Sets the Anthropic model using a {@link ChatModel} enum value.
		 * @param model the chat model enum
		 * @return this builder instance for method chaining
		 */
		public Builder model(ChatModel model) {
			Assert.notNull(model, "model cannot be null");
			this.model = model.getValue();
			return this;
		}

		private String model = DEFAULT_MODEL;

		/**
		 * Sets the custom RestClient.Builder for HTTP requests.
		 * @param restClientBuilder the RestClient builder
		 * @return this builder instance for method chaining
		 */
		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		private RestClient.Builder restClientBuilder = RestClient.builder();

		/**
		 * Sets the custom WebClient.Builder for reactive HTTP requests.
		 * @param webClientBuilder the WebClient builder
		 * @return this builder instance for method chaining
		 */
		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "webClientBuilder cannot be null");
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		private WebClient.Builder webClientBuilder = WebClient.builder();

		/**
		 * Sets the custom response error handler for HTTP responses.
		 * @param responseErrorHandler the error handler
		 * @return this builder instance for method chaining
		 */
		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		/**
		 * Builds and returns a new {@link VertexAiAnthropicApi} instance with the
		 * configured parameters.
		 * @return the constructed API client instance
		 */
		public VertexAiAnthropicApi build() {
			Assert.notNull(this.credentials, "credentials cannot be null");
			Assert.notNull(this.projectId, "projectId cannot be null");
			String baseUrl = "https://%saiplatform.googleapis.com"
				.formatted(this.location.equals(LOCATION_GLOBAL) ? "" : (this.location + "-"));
			String completionPath = "/v1/projects/%s/locations/%s/publishers/anthropic/models/%s:streamRawPredict"
				.formatted(this.projectId, this.location, this.model);
			return new VertexAiAnthropicApi(baseUrl, completionPath, this.credentials, this.restClientBuilder,
					this.webClientBuilder, this.responseErrorHandler);
		}

	}

	/**
	 * Claude models names. <a href=
	 * "https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/claude/use-claude#model-list">link</a>
	 */
	public enum ChatModel implements ChatModelDescription {

		/**
		 * Claude Opus 4.5
		 */
		CLAUDE_OPUS_4_5("claude-opus-4-5@20251101"),

		/**
		 * Claude Opus 4.1
		 */
		CLAUDE_OPUS_4_1("claude-opus-4-1@20250805"),

		/**
		 * Claude Opus 4
		 */
		CLAUDE_OPUS_4("claude-opus-4@20250514"),

		/**
		 * Claude Sonnet 4.5
		 */
		CLAUDE_SONNET_4_5("claude-sonnet-4-5@20250929"),

		/**
		 * Claude Sonnet 4
		 */
		CLAUDE_SONNET_4("claude-sonnet-4@20250514"),

		/**
		 * Claude 3.7 Sonnet
		 */
		CLAUDE_3_7_SONNET("claude-3-7-sonnet@20250219"),

		/**
		 * Claude 3.5 Sonnet v2
		 */
		CLAUDE_3_5_SONNET_V2("claude-3-5-sonnet-v2@20241022"),

		/**
		 * Claude Haiku 4.5
		 */
		CLAUDE_4_5_HAIKU("claude-haiku-4-5@20251001"),

		/**
		 * Claude 3.5 Haiku
		 */
		CLAUDE_3_5_HAIKU("claude-3-5-haiku@20241022"),

		/**
		 * Claude 3.5 Sonnet
		 */
		CLAUDE_3_5_SONNET("claude-3-5-sonnet@20240620"),

		/**
		 * Claude 3 Opus
		 */
		CLAUDE_3_OPUS("claude-3-opus@20240229"),

		/**
		 * Claude 3 Haiku
		 */
		CLAUDE_3_HAIKU("claude-3-haiku@20240307");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

}
