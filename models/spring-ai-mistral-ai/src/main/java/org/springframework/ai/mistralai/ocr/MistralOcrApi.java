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

package org.springframework.ai.mistralai.ocr;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
 * Java Client library for the Mistral AI OCR API. Provides access to the OCR
 * functionality.
 * <p>
 * The API processes a document and returns a markdown string representation of the text,
 * along with information about extracted images.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
public class MistralOcrApi {

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private final RestClient restClient;

	/**
	 * Create a new MistralOcrApi instance.
	 * @param mistralAiApiKey Mistral AI API key.
	 */
	public MistralOcrApi(String mistralAiApiKey) {
		this(DEFAULT_BASE_URL, mistralAiApiKey);
	}

	/**
	 * Create a new MistralOcrApi instance.
	 * @param baseUrl API base URL.
	 * @param mistralAiApiKey Mistral AI API key.
	 */
	public MistralOcrApi(String baseUrl, String mistralAiApiKey) {
		this(baseUrl, mistralAiApiKey, RestClient.builder());
	}

	/**
	 * Create a new MistralOcrApi instance.
	 * @param baseUrl API base URL.
	 * @param mistralAiApiKey Mistral AI API key.
	 * @param restClientBuilder RestClient builder.
	 */
	public MistralOcrApi(String baseUrl, String mistralAiApiKey, RestClient.Builder restClientBuilder) {
		this(baseUrl, mistralAiApiKey, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new MistralOcrApi instance.
	 * @param baseUrl API base URL.
	 * @param mistralAiApiKey Mistral AI API key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public MistralOcrApi(String baseUrl, String mistralAiApiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(mistralAiApiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	/**
	 * Performs OCR on a document and returns the extracted information.
	 * @param ocrRequest The OCR request containing document details and processing
	 * options.
	 * @return ResponseEntity containing the OCR response with markdown text and image
	 * data.
	 */
	public ResponseEntity<OCRResponse> ocr(OCRRequest ocrRequest) {

		Assert.notNull(ocrRequest, "The request body can not be null.");
		Assert.notNull(ocrRequest.model(), "The model can not be null.");
		Assert.notNull(ocrRequest.document(), "The document can not be null.");

		return this.restClient.post().uri("/v1/ocr").body(ocrRequest).retrieve().toEntity(OCRResponse.class);
	}

	/**
	 * List of well-known Mistral OCR models.
	 */
	public enum OCRModel {

		MISTRAL_OCR_LATEST("mistral-ocr-latest");

		private final String value;

		OCRModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Represents the request for the OCR API.
	 *
	 * @param model Model to use for OCR. Can be 'mistral-ocr-latest'
	 * @param id An optional string identifier.
	 * @param document Document to run OCR on. Can be either a {@link DocumentURLChunk} or
	 * an {@link ImageURLChunk}.
	 * @param pages Specific pages to process in various formats: single number, range, or
	 * list of both. Starts from 0.
	 * @param includeImageBase64 Whether to include image URLs in the response.
	 * @param imageLimit Maximum number of images to extract.
	 * @param imageMinSize Minimum height and width of image to extract.
	 */
	@JsonInclude(Include.NON_NULL)
	public record OCRRequest(@JsonProperty("model") String model, @JsonProperty("id") String id,
			@JsonProperty("document") Document document, @JsonProperty("pages") List<Integer> pages,
			@JsonProperty("include_image_base64") Boolean includeImageBase64,
			@JsonProperty("image_limit") Integer imageLimit, @JsonProperty("image_min_size") Integer imageMinSize) {

		/**
		 * Represents the document to be processed, which can be either a document URL or
		 * an image URL. Only one of the fields should be set.
		 */
		@JsonInclude(Include.NON_NULL)
		public sealed interface Document permits DocumentURLChunk, ImageURLChunk {

		}

		/**
		 * Represents a document URL chunk.
		 *
		 * @param type Must be 'document_url'.
		 * @param documentUrl URL of the document.
		 * @param documentName Optional name of the document.
		 */
		@JsonInclude(Include.NON_NULL)
		public record DocumentURLChunk(

				@JsonProperty("type") String type, @JsonProperty("document_url") String documentUrl,
				@JsonProperty("document_name") @Nullable String documentName) implements Document {

			/**
			 * Create a DocumentURLChunk.
			 * @param documentUrl URL of the document.
			 */
			public DocumentURLChunk(String documentUrl) {
				this("document_url", documentUrl, null);
			}
		}

		/**
		 * Represents an image URL chunk.
		 *
		 * @param type Must be 'image_url'.
		 * @param imageUrl URL of the image.
		 * @param imageName Optional name of the image.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ImageURLChunk(

				@JsonProperty("type") String type, @JsonProperty("image_url") String imageUrl,
				@JsonProperty("image_name") @Nullable String imageName) implements Document {

			/**
			 * Create an ImageURLChunk.
			 * @param imageUrl URL of the image.
			 */
			public ImageURLChunk(String imageUrl) {
				this("image_url", imageUrl, null);
			}
		}
	}

	/**
	 * Represents the response from the OCR API.
	 *
	 * @param pages List of OCR info for pages.
	 * @param model The model used to generate the OCR.
	 * @param usageInfo Usage info for the OCR request.
	 * @param pagesProcessed Number of pages processed.
	 * @param docSizeBytes Document size in bytes.
	 */
	@JsonInclude(Include.NON_NULL)
	public record OCRResponse(@JsonProperty("pages") List<OCRPage> pages, @JsonProperty("model") String model,
			@JsonProperty("usage_info") OCRUsageInfo usageInfo, @JsonProperty("pages_processed") Integer pagesProcessed,
			@JsonProperty("doc_size_bytes") Integer docSizeBytes) {

	}

	/**
	 * Represents OCR information for a single page.
	 *
	 * @param index The page index in a PDF document starting from 0.
	 * @param markdown The markdown string response of the page.
	 * @param images List of all extracted images in the page.
	 * @param dimensions The dimensions of the PDF Page's screenshot image.
	 */
	@JsonInclude(Include.NON_NULL)
	public record OCRPage(@JsonProperty("index") Integer index, @JsonProperty("markdown") String markdown,
			@JsonProperty("images") List<ExtractedImage> images,
			@JsonProperty("dimensions") OCRPageDimensions dimensions) {
	}

	/**
	 * Represents an extracted image from a page.
	 *
	 * @param id Image ID for the extracted image in a page.
	 * @param topLeftX X coordinate of the top-left corner of the extracted image.
	 * @param topLeftY Y coordinate of the top-left corner of the extracted image.
	 * @param bottomRightX X coordinate of the bottom-right corner of the extracted image.
	 * @param bottomRightY Y coordinate of the bottom-right corner of the extracted image.
	 * @param imageBase64 Base64 string of the extracted image.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ExtractedImage(@JsonProperty("id") String id, @JsonProperty("top_left_x") Integer topLeftX,
			@JsonProperty("top_left_y") Integer topLeftY, @JsonProperty("bottom_right_x") Integer bottomRightX,
			@JsonProperty("bottom_right_y") Integer bottomRightY, @JsonProperty("image_base64") String imageBase64) {

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof ExtractedImage that)) {
				return false;
			}
			return Objects.equals(this.id, that.id) && Objects.equals(this.topLeftX, that.topLeftX)
					&& Objects.equals(this.topLeftY, that.topLeftY)
					&& Objects.equals(this.bottomRightX, that.bottomRightX)
					&& Objects.equals(this.bottomRightY, that.bottomRightY)
					&& Objects.equals(this.imageBase64, that.imageBase64);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.id, this.topLeftX, this.topLeftY, this.bottomRightX, this.bottomRightY,
					this.imageBase64);
		}
	}

	/**
	 * Represents the dimensions of a PDF page's screenshot image.
	 *
	 * @param dpi Dots per inch of the page-image.
	 * @param height Height of the image in pixels.
	 * @param width Width of the image in pixels.
	 */
	@JsonInclude(Include.NON_NULL)
	public record OCRPageDimensions(@JsonProperty("dpi") Integer dpi, @JsonProperty("height") Integer height,
			@JsonProperty("width") Integer width) {
	}

	/**
	 * Represents usage information for the OCR request.
	 *
	 * @param pagesProcessed Number of pages processed.
	 * @param docSizeBytes Document size in bytes.
	 */
	@JsonInclude(Include.NON_NULL)
	public record OCRUsageInfo(@JsonProperty("pages_processed") Integer pagesProcessed,
			@JsonProperty("doc_size_bytes") Integer docSizeBytes) {
	}

}
