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

package org.springframework.ai.mistralai.ocr;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelOptions;

/**
 * Options for Mistral AI OCR requests. These options are used at runtime when making an
 * OCR call.
 *
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 * @since 1.1.0
 */
public class MistralAiOcrOptions implements ModelOptions {

	/**
	 * The model to use for OCR. Defaults to mistral-ocr-latest.
	 */
	private final String model;

	/**
	 * An optional string identifier for the request.
	 */
	private final @Nullable String id;

	/**
	 * Specific pages to process in various formats: single number, range, or list of
	 * both. Starts from 0.
	 */
	private final @Nullable List<Integer> pages;

	/**
	 * Whether to include base64 encoded image data in the response.
	 */
	private final @Nullable Boolean includeImageBase64;

	/**
	 * Maximum number of images to extract per page.
	 */
	private final @Nullable Integer imageLimit;

	/**
	 * Minimum height and width (in pixels) of images to extract.
	 */
	private final @Nullable Integer imageMinSize;

	protected MistralAiOcrOptions(@Nullable String model, @Nullable String id, @Nullable List<Integer> pages,
			@Nullable Boolean includeImageBase64, @Nullable Integer imageLimit, @Nullable Integer imageMinSize) {
		this.model = (model != null ? model : MistralOcrApi.OCRModel.MISTRAL_OCR_LATEST.getValue());
		this.id = id;
		this.pages = pages != null ? List.copyOf(pages) : null;
		this.includeImageBase64 = includeImageBase64;
		this.imageLimit = imageLimit;
		this.imageMinSize = imageMinSize;
	}

	public static MistralAiOcrOptions.Builder builder() {
		return new Builder();
	}

	public String getModel() {
		return this.model;
	}

	public @Nullable String getId() {
		return this.id;
	}

	public @Nullable List<Integer> getPages() {
		return this.pages;
	}

	public @Nullable Boolean getIncludeImageBase64() {
		return this.includeImageBase64;
	}

	public @Nullable Integer getImageLimit() {
		return this.imageLimit;
	}

	public @Nullable Integer getImageMinSize() {
		return this.imageMinSize;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MistralAiOcrOptions that = (MistralAiOcrOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.id, that.id)
				&& Objects.equals(this.pages, that.pages)
				&& Objects.equals(this.includeImageBase64, that.includeImageBase64)
				&& Objects.equals(this.imageLimit, that.imageLimit)
				&& Objects.equals(this.imageMinSize, that.imageMinSize);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.id, this.pages, this.includeImageBase64, this.imageLimit,
				this.imageMinSize);
	}

	public static final class Builder {

		private String model = MistralOcrApi.OCRModel.MISTRAL_OCR_LATEST.getValue();

		private @Nullable String id;

		private @Nullable List<Integer> pages;

		private @Nullable Boolean includeImageBase64;

		private @Nullable Integer imageLimit;

		private @Nullable Integer imageMinSize;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder pages(List<Integer> pages) {
			this.pages = pages;
			return this;
		}

		public Builder includeImageBase64(@Nullable Boolean includeImageBase64) {
			this.includeImageBase64 = includeImageBase64;
			return this;
		}

		public Builder imageLimit(Integer imageLimit) {
			this.imageLimit = imageLimit;
			return this;
		}

		public Builder imageMinSize(Integer imageMinSize) {
			this.imageMinSize = imageMinSize;
			return this;
		}

		public MistralAiOcrOptions build() {
			return new MistralAiOcrOptions(this.model, this.id, this.pages, this.includeImageBase64, this.imageLimit,
					this.imageMinSize);
		}

	}

}
