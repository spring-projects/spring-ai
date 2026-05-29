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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.ocr.MistralAiOcrOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Mistral AI OCR.
 *
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 * @since 1.1.0
 */
@ConfigurationProperties(MistralAiOcrProperties.CONFIG_PREFIX)
public class MistralAiOcrProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.ocr";

	private final Options options = new Options();

	public MistralAiOcrProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public Options getOptions() {
		return this.options;
	}

	public static class Options {

		private @Nullable String model;

		private @Nullable String id;

		private @Nullable Boolean includeImageBase64;

		private @Nullable List<Integer> pages;

		private @Nullable Integer imageLimit;

		private @Nullable Integer imageMinSize;

		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(@Nullable String model) {
			this.model = model;
		}

		public @Nullable Boolean getIncludeImageBase64() {
			return this.includeImageBase64;
		}

		public void setIncludeImageBase64(@Nullable Boolean includeImageBase64) {
			this.includeImageBase64 = includeImageBase64;
		}

		public MistralAiOcrOptions toOptions() {
			MistralAiOcrOptions.Builder builder = MistralAiOcrOptions.builder();
			if (this.model != null) {
				builder.model(this.model);
			}
			if (this.includeImageBase64 != null) {
				builder.includeImageBase64(this.includeImageBase64);
			}
			return builder.build();
		}

		public @Nullable String getId() {
			return this.id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		public @Nullable List<Integer> getPages() {
			return this.pages;
		}

		public void setPages(@Nullable List<Integer> pages) {
			this.pages = pages;
		}

		public @Nullable Integer getImageLimit() {
			return this.imageLimit;
		}

		public void setImageLimit(@Nullable Integer imageLimit) {
			this.imageLimit = imageLimit;
		}

		public @Nullable Integer getImageMinSize() {
			return this.imageMinSize;
		}

		public void setImageMinSize(@Nullable Integer imageMinSize) {
			this.imageMinSize = imageMinSize;
		}

	}

}
