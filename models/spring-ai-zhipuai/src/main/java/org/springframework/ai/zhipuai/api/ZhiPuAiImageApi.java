/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.zhipuai.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * ZhiPuAI Image API.
 *
 * @see <a href= "https://open.bigmodel.cn/dev/howuse/cogview">CogView Images</a>
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class ZhiPuAiImageApi {

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.CogView_3.getValue();

	private final RestClient restClient;

	/**
	 * Create a new ZhiPuAI Image api with base URL set to
	 * {@code https://api.ZhiPuAI.com}.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 */
	public ZhiPuAiImageApi(String zhiPuAiToken) {
		this(ZhiPuApiConstants.DEFAULT_BASE_URL, zhiPuAiToken, RestClient.builder());
	}

	/**
	 * Create a new ZhiPuAI Image API with the provided base URL.
	 * @param baseUrl the base URL for the ZhiPuAI API.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @param restClientBuilder the rest client builder to use.
	 */
	public ZhiPuAiImageApi(String baseUrl, String zhiPuAiToken, RestClient.Builder restClientBuilder) {
		this(baseUrl, zhiPuAiToken, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new ZhiPuAI Image API with the provided base URL.
	 * @param baseUrl the base URL for the ZhiPuAI API.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @param restClientBuilder the rest client builder to use.
	 * @param responseErrorHandler the response error handler to use.
	 */
	public ZhiPuAiImageApi(String baseUrl, String zhiPuAiToken, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(h -> h.setBearerAuth(zhiPuAiToken)
		// h.setContentType(MediaType.APPLICATION_JSON);
		).defaultStatusHandler(responseErrorHandler).build();
	}

	public ResponseEntity<ZhiPuAiImageResponse> createImage(ZhiPuAiImageRequest zhiPuAiImageRequest) {
		Assert.notNull(zhiPuAiImageRequest, "Image request cannot be null.");
		Assert.hasLength(zhiPuAiImageRequest.prompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri("/v4/images/generations")
			.body(zhiPuAiImageRequest)
			.retrieve()
			.toEntity(ZhiPuAiImageResponse.class);
	}

	/**
	 * ZhiPuAI Image API model.
	 * <a href="https://open.bigmodel.cn/dev/howuse/cogview">CogView</a>
	 */
	public enum ImageModel {

		CogView_3("cogview-3");

		private final String value;

		ImageModel(String model) {
			this.value = model;
		}

		public String getValue() {
			return this.value;
		}

	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ZhiPuAiImageRequest(
		@JsonProperty("prompt") String prompt,
		@JsonProperty("model") String model,
		@JsonProperty("user_id") String user) {

		public ZhiPuAiImageRequest(String prompt, String model) {
			this(prompt, model, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ZhiPuAiImageResponse(
		@JsonProperty("created") Long created,
		@JsonProperty("data") List<Data> data) {
	}
	// @formatter:onn

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(@JsonProperty("url") String url) {

	}

}
