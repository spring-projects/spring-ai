/*
 * Copyright 2024-2024 the original author or authors.
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

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiClientErrorException;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.api.OpenAiApi.ResponseError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Image API.
 *
 * @see <a href=
 * "https://platform.openai.com/docs/api-reference/images">https://platform.openai.com/docs/api-reference/images</a>
 */
public class OpenAiImageApi {

	private static final String DEFAULT_BASE_URL = "https://api.openai.com";

	public static final String DEFAULT_IMAGE_MODEL = "dall-e-2";

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	/**
	 * Create a new OpenAI Image api with base URL set to https://api.openai.com
	 * @param openAiToken OpenAI apiKey.
	 */
	public OpenAiImageApi(String openAiToken) {
		this(DEFAULT_BASE_URL, openAiToken, RestClient.builder());
	}

	public OpenAiImageApi(String baseUrl, String openAiToken, RestClient.Builder restClientBuilder) {

		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(openAiToken);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		var responseErrorHandler = new ResponseErrorHandler() {

			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				if (response.getStatusCode().isError()) {
					if (response.getStatusCode().is4xxClientError()) {
						throw new OpenAiApiClientErrorException(String.format("%s - %s",
								response.getStatusCode().value(),
								OpenAiImageApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
					}
					throw new OpenAiApiException(String.format("%s - %s", response.getStatusCode().value(),
							OpenAiImageApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
				}
			}
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiImageRequest (
		@JsonProperty("prompt") String prompt,
		@JsonProperty("model") String model,
		@JsonProperty("n") Integer n,
		@JsonProperty("quality") String quality,
		@JsonProperty("response_format") String responseFormat,
		@JsonProperty("size") String size,
		@JsonProperty("style") String style,
		@JsonProperty("user") String user) {

		public OpenAiImageRequest(String prompt, String model) {
			this(prompt, model, null, null, null, null, null, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiImageResponse(
		@JsonProperty("created") Long created,
		@JsonProperty("data") List<Data> data) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Data(
		@JsonProperty("url") String url,
		@JsonProperty("b64_json") String b64Json,
		@JsonProperty("revised_prompt") String revisedPrompt) {
	}
	// @formatter:onn

	public ResponseEntity<OpenAiImageResponse> createImage(OpenAiImageRequest openAiImageRequest) {
		Assert.notNull(openAiImageRequest, "Image request cannot be null.");
		Assert.hasLength(openAiImageRequest.prompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri("v1/images/generations")
			.body(openAiImageRequest)
			.retrieve()
			.toEntity(OpenAiImageResponse.class);
	}

}
