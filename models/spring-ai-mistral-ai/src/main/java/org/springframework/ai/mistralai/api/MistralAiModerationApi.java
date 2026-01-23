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

package org.springframework.ai.mistralai.api;

import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Mistral AI Moderation API.
 *
 * @author Ricken Bazolo
 * @author Jason Smith
 * @see <a href= "https://docs.mistral.ai/capabilities/guardrailing/">Moderation</a>
 */
public class MistralAiModerationApi {

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private final RestClient restClient;

	public MistralAiModerationApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	public ResponseEntity<MistralAiModerationResponse> moderate(MistralAiModerationRequest mistralAiModerationRequest) {
		Assert.notNull(mistralAiModerationRequest, "Moderation request cannot be null.");
		Assert.hasLength(mistralAiModerationRequest.prompt(), "Prompt cannot be empty.");
		Assert.notNull(mistralAiModerationRequest.model(), "Model cannot be null.");

		return this.restClient.post()
			.uri("v1/moderations")
			.body(mistralAiModerationRequest)
			.retrieve()
			.toEntity(MistralAiModerationResponse.class);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey cannot be null or empty");
			this.apiKey = apiKey;
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

		public MistralAiModerationApi build() {
			return new MistralAiModerationApi(this.baseUrl, this.apiKey, this.restClientBuilder,
					this.responseErrorHandler);
		}

	}

	/**
	 * List of well-known Mistral moderation models.
	 *
	 * @see <a href=
	 * "https://docs.mistral.ai/getting-started/models/models_overview/">Mistral AI Models
	 * Overview</a>
	 */
	public enum Model {

		// @formatter:off
		MISTRAL_MODERATION("mistral-moderation-latest");
		// @formatter:on

		private final String value;

		Model(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MistralAiModerationRequest(
		@JsonProperty("input") String prompt,
		@JsonProperty("model") String model
	) {

		public MistralAiModerationRequest(String prompt) {
			this(prompt, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MistralAiModerationResponse(
			@JsonProperty("id") String id,
			@JsonProperty("model") String model,
			@JsonProperty("results") MistralAiModerationResult[] results) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MistralAiModerationResult(
			@JsonProperty("categories") Categories categories,
			@JsonProperty("category_scores") CategoryScores categoryScores) {

		public boolean flagged() {
			return this.categories != null && (this.categories.sexual() || this.categories.hateAndDiscrimination() || this.categories.violenceAndThreats()
					|| this.categories.selfHarm() || this.categories.dangerousAndCriminalContent() || this.categories.health()
					|| this.categories.financial() || this.categories.law() || this.categories.pii());
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Categories(
			@JsonProperty("sexual") boolean sexual,
			@JsonProperty("hate_and_discrimination") boolean hateAndDiscrimination,
			@JsonProperty("violence_and_threats") boolean violenceAndThreats,
			@JsonProperty("selfharm") boolean selfHarm,
			@JsonProperty("dangerous_and_criminal_content") boolean dangerousAndCriminalContent,
			@JsonProperty("health") boolean health,
			@JsonProperty("financial") boolean financial,
			@JsonProperty("law") boolean law,
			@JsonProperty("pii") boolean pii)  {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CategoryScores(
			@JsonProperty("sexual") double sexual,
			@JsonProperty("hate_and_discrimination") double hateAndDiscrimination,
			@JsonProperty("violence_and_threats") double violenceAndThreats,
			@JsonProperty("selfharm") double selfHarm,
			@JsonProperty("dangerous_and_criminal_content") double dangerousAndCriminalContent,
			@JsonProperty("health") double health,
			@JsonProperty("financial") double financial,
			@JsonProperty("law") double law,
			@JsonProperty("pii") double pii)  {

	}
	// @formatter:on

}
