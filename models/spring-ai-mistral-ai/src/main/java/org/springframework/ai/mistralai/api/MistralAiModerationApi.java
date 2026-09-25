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

package org.springframework.ai.mistralai.api;

import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

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
 * @author Nicolas Krier
 * @see <a href= "https://docs.mistral.ai/studio-api/conversations/moderation">Moderation
 * and Guardrailing</a>
 */
public class MistralAiModerationApi {

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private final RestClient restClient;

	public MistralAiModerationApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
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

		private @Nullable String apiKey;

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
			Assert.state(this.apiKey != null, "The API key must not be null");
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

		MISTRAL_MODERATION("mistral-moderation-latest");

		private final String value;

		Model(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	// @formatter:off
	public record MistralAiModerationRequest(
		@JsonProperty("input") String prompt,
		@JsonProperty("model") String model
	) {

	}

	public record MistralAiModerationResponse(
			@JsonProperty("id") String id,
			@JsonProperty("model") String model,
			@JsonProperty("results") MistralAiModerationResult[] results) {

	}

	public record MistralAiModerationResult(
			@JsonProperty("categories") Categories categories,
			@JsonProperty("category_scores") CategoryScores categoryScores) {

		public boolean flagged() {
			return (Boolean.TRUE.equals(this.categories.sexual())
					|| Boolean.TRUE.equals(this.categories.hateAndDiscrimination())
					|| Boolean.TRUE.equals(this.categories.violenceAndThreats())
					|| Boolean.TRUE.equals(this.categories.selfHarm())
					|| Boolean.TRUE.equals(this.categories.dangerous())
					|| Boolean.TRUE.equals(this.categories.criminal())
					|| Boolean.TRUE.equals(this.categories.health())
					|| Boolean.TRUE.equals(this.categories.financial())
					|| Boolean.TRUE.equals(this.categories.law())
					|| Boolean.TRUE.equals(this.categories.pii())
					|| Boolean.TRUE.equals(this.categories.jailbreaking()));
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Categories(
			@JsonProperty("sexual") @Nullable Boolean sexual,
			@JsonProperty("hate_and_discrimination") @Nullable Boolean hateAndDiscrimination,
			@JsonProperty("violence_and_threats") @Nullable Boolean violenceAndThreats,
			@JsonProperty("selfharm") @Nullable Boolean selfHarm,
			@JsonProperty("dangerous") @Nullable Boolean dangerous,
			@JsonProperty("criminal") @Nullable Boolean criminal,
			@JsonProperty("health") @Nullable Boolean health,
			@JsonProperty("financial") @Nullable Boolean financial,
			@JsonProperty("law") @Nullable Boolean law,
			@JsonProperty("pii") @Nullable Boolean pii,
			@JsonProperty("jailbreaking") @Nullable Boolean jailbreaking) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CategoryScores(
			@JsonProperty("sexual") @Nullable Double sexual,
			@JsonProperty("hate_and_discrimination") @Nullable Double hateAndDiscrimination,
			@JsonProperty("violence_and_threats") @Nullable Double violenceAndThreats,
			@JsonProperty("selfharm") @Nullable Double selfHarm,
			@JsonProperty("dangerous") @Nullable Double dangerous,
			@JsonProperty("criminal") @Nullable Double criminal,
			@JsonProperty("health") @Nullable Double health,
			@JsonProperty("financial") @Nullable Double financial,
			@JsonProperty("law") @Nullable Double law,
			@JsonProperty("pii") @Nullable Double pii,
			@JsonProperty("jailbreaking") @Nullable Double jailbreaking) {

	}
	// @formatter:on

}
