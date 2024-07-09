/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.embedding.multimodal;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiMultimodalEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_MODEL_NAME = VertexAiMultimodalEmbeddingModelName.MULTIMODAL_EMBEDDING_001
		.getTextName();

	// @formatter:off
	/**
	 * The embedding model name to use. Supported models are:
	 * text-embedding-004, text-multilingual-embedding-002 and multimodalembedding@001.
	 */
	private @JsonProperty("model") String model;

	/**
	 * The number of dimensions the resulting output embeddings should have.
	 * Supported for model version 004 and later. You can use this parameter to reduce the
	 * embedding size, for example, for storage optimization.
	 */
	private @JsonProperty("dimensions") Integer dimensions;

	/**
	 * The start offset of the video segment in seconds. If not specified, it's calculated with max(0, endOffsetSec - 120).
	 */
	private @JsonProperty("videoStartOffsetSec") Integer videoStartOffsetSec;

	
	/**
	 * The end offset of the video segment in seconds. If not specified, it's calculated with min(video length, startOffSec + 120).
	 * If both startOffSec and endOffSec are specified, endOffsetSec is adjusted to min(startOffsetSec+120, endOffsetSec).
	 */
	private @JsonProperty("videoEndOffsetSec") Integer videoEndOffsetSec;
	
	/**
	 * The interval of the video the embedding will be generated. The minimum value for interval_sec is 4.
	 * If the interval is less than 4, an InvalidArgumentError is returned. There are no limitations on the maximum value
	 * of the interval. However, if the interval is larger than min(video length, 120s), it impacts the quality of the
	 * generated embeddings. Default value: 16.
	 */
	private @JsonProperty("videoIntervalSec") Integer videoIntervalSec;


	// @formatter:on
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected VertexAiMultimodalEmbeddingOptions options;

		public Builder() {
			this.options = new VertexAiMultimodalEmbeddingOptions();
		}

		public Builder from(VertexAiMultimodalEmbeddingOptions fromOptions) {
			if (fromOptions.getDimensions() != null) {
				this.options.setDimensions(fromOptions.getDimensions());
			}
			if (StringUtils.hasText(fromOptions.getModel())) {
				this.options.setModel(fromOptions.getModel());
			}
			if (fromOptions.getVideoStartOffsetSec() != null) {
				this.options.setVideoStartOffsetSec(fromOptions.getVideoStartOffsetSec());
			}
			if (fromOptions.getVideoEndOffsetSec() != null) {
				this.options.setVideoEndOffsetSec(fromOptions.getVideoEndOffsetSec());
			}
			if (fromOptions.getVideoIntervalSec() != null) {
				this.options.setVideoIntervalSec(fromOptions.getVideoIntervalSec());
			}
			return this;
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withModel(VertexAiMultimodalEmbeddingModelName model) {
			this.options.setModel(model.getTextName());
			return this;
		}

		public Builder withDimensions(Integer dimensions) {
			this.options.setDimensions(dimensions);
			return this;
		}

		public Builder withVideoStartOffsetSec(Integer videoStartOffsetSec) {
			this.options.setVideoStartOffsetSec(videoStartOffsetSec);
			return this;
		}

		public Builder withVideoEndOffsetSec(Integer videoEndOffsetSec) {
			this.options.setVideoEndOffsetSec(videoEndOffsetSec);
			return this;
		}

		public Builder withVideoIntervalSec(Integer videoIntervalSec) {
			this.options.setVideoIntervalSec(videoIntervalSec);
			return this;
		}

		public VertexAiMultimodalEmbeddingOptions build() {
			return this.options;
		}

	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public Integer getVideoStartOffsetSec() {
		return this.videoStartOffsetSec;
	}

	public void setVideoStartOffsetSec(Integer videoStartOffsetSec) {
		this.videoStartOffsetSec = videoStartOffsetSec;
	}

	public Integer getVideoEndOffsetSec() {
		return this.videoEndOffsetSec;
	}

	public void setVideoEndOffsetSec(Integer videoEndOffsetSec) {
		this.videoEndOffsetSec = videoEndOffsetSec;
	}

	public Integer getVideoIntervalSec() {
		return this.videoIntervalSec;
	}

	public void setVideoIntervalSec(Integer videoIntervalSec) {
		this.videoIntervalSec = videoIntervalSec;
	}

}
