package org.springframework.ai.openai.api;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
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

public class OpenAiImageApi {

	private static final String DEFAULT_BASE_URL = "https://api.openai.com";

	public static final String DEFAULT_IMAGE_MODEL = "dall-e-2";

	// Assuming RestClient and WebClient are properly defined somewhere
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

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class OpenAiImageRequest {

		@JsonProperty("prompt")
		private String prompt;

		@JsonProperty("model")
		private String model = DEFAULT_IMAGE_MODEL;

		@JsonProperty("n")
		private Integer n;

		@JsonProperty("quality")
		private String quality;

		@JsonProperty("response_format")
		private String responseFormat;

		@JsonProperty("size")
		private String size;

		@JsonProperty("style")
		private String style;

		@JsonProperty("user")
		private String user;

		public OpenAiImageRequest() {
		}

		public OpenAiImageRequest(String prompt, String model, Integer n, String quality, String size,
				String responseFormat, String style, String user) {
			this.prompt = prompt;
			this.model = model;
			this.n = n;
			this.quality = quality;
			this.size = size;
			this.responseFormat = responseFormat;
			this.style = style;
			this.user = user;
		}

		public String getPrompt() {
			return prompt;
		}

		public void setPrompt(String prompt) {
			this.prompt = prompt;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		public Integer getN() {
			return n;
		}

		public void setN(Integer n) {
			this.n = n;
		}

		public String getQuality() {
			return quality;
		}

		public void setQuality(String quality) {
			this.quality = quality;
		}

		public String getSize() {
			return size;
		}

		public void setSize(String size) {
			this.size = size;
		}

		public String getResponseFormat() {
			return responseFormat;
		}

		public void setResponseFormat(String responseFormat) {
			this.responseFormat = responseFormat;
		}

		public String getStyle() {
			return style;
		}

		public void setStyle(String style) {
			this.style = style;
		}

		public String getUser() {
			return user;
		}

		public void setUser(String user) {
			this.user = user;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof OpenAiImageRequest that))
				return false;
			return Objects.equals(prompt, that.prompt) && Objects.equals(model, that.model) && Objects.equals(n, that.n)
					&& Objects.equals(quality, that.quality) && Objects.equals(size, that.size)
					&& Objects.equals(responseFormat, that.responseFormat) && Objects.equals(style, that.style)
					&& Objects.equals(user, that.user);
		}

		@Override
		public int hashCode() {
			return Objects.hash(prompt, model, n, quality, size, responseFormat, style, user);
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiImageResponse(@JsonProperty("created") Long created, @JsonProperty("data") List<Data> data) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Data(@JsonProperty("url") String url, @JsonProperty("b64_json") String b64Json,
			@JsonProperty("revised_prompt") String revisedPrompt) {

	}

	public ResponseEntity<OpenAiImageResponse> createImage(OpenAiImageRequest openAiImageRequest) {
		Assert.notNull(openAiImageRequest, "Image request cannot be null.");
		Assert.hasLength(openAiImageRequest.getPrompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri("v1/images/generations")
			.body(openAiImageRequest)
			.retrieve()
			.toEntity(OpenAiImageResponse.class);
	}

}
