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

package org.springframework.ai.vertexai.embedding.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.util.StringUtils;

/**
 * Options for the Vertex AI Text Embedding service.
 *
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiTextEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_MODEL_NAME = VertexAiTextEmbeddingModelName.TEXT_EMBEDDING_004.getName();

	/**
	 * The embedding model name to use. Supported models are: text-embedding-004,
	 * text-multilingual-embedding-002 and multimodalembedding@001.
	 */
	private @JsonProperty("model") String model;

	// @formatter:off

	/**
	 * The intended downstream application to help the model produce better quality embeddings.
	 * Not all model versions support all task types.
	 */
	private @JsonProperty("task") TaskType taskType;

	/**
	 * The number of dimensions the resulting output embeddings should have.
	 * Supported for model version 004 and later. You can use this parameter to reduce the
	 * embedding size, for example, for storage optimization.
	 */
	private @JsonProperty("dimensions") Integer dimensions;

	/**
	 * Optional title, only valid with task_type=RETRIEVAL_DOCUMENT.
	 */
	private @JsonProperty("title") String title;

	/**
	 * When set to true, input text will be truncated. When set to false, an error is returned
	 * if the input text is longer than the maximum length supported by the model. Defaults to true.
	 */
	private @JsonProperty("autoTruncate") Boolean autoTruncate;

	public static Builder builder() {
		return new Builder();
	}


	// @formatter:on

	public VertexAiTextEmbeddingOptions initializeDefaults() {

		if (this.getTaskType() == null) {
			this.setTaskType(TaskType.RETRIEVAL_DOCUMENT);
		}

		if (StringUtils.hasText(this.getTitle()) && this.getTaskType() != TaskType.RETRIEVAL_DOCUMENT) {
			throw new IllegalArgumentException("Title is only valid with task_type=RETRIEVAL_DOCUMENT");
		}

		return this;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public TaskType getTaskType() {
		return this.taskType;
	}

	public void setTaskType(TaskType taskType) {
		this.taskType = taskType;
	}

	@Override
	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String user) {
		this.title = user;
	}

	public Boolean getAutoTruncate() {
		return this.autoTruncate;
	}

	public void setAutoTruncate(Boolean autoTruncate) {
		this.autoTruncate = autoTruncate;
	}

	public enum TaskType {

		/**
		 * Specifies the given text is a document in a search/retrieval setting.
		 */
		RETRIEVAL_QUERY,

		/**
		 * Specifies the given text is a query in a search/retrieval setting.
		 */
		RETRIEVAL_DOCUMENT,

		/**
		 * Specifies the given text will be used for semantic textual similarity (STS).
		 */
		SEMANTIC_SIMILARITY,

		/**
		 * Specifies that the embeddings will be used for classification.
		 */
		CLASSIFICATION,

		/**
		 * Specifies that the embeddings will be used for clustering.
		 */
		CLUSTERING,

		/**
		 * Specifies that the query embedding is used for answering questions. Use
		 * RETRIEVAL_DOCUMENT for the document side.
		 */
		QUESTION_ANSWERING,

		/**
		 * Specifies that the query embedding is used for fact verification.
		 */
		FACT_VERIFICATION

	}

	public static class Builder {

		protected VertexAiTextEmbeddingOptions options;

		public Builder() {
			this.options = new VertexAiTextEmbeddingOptions();
		}

		public Builder from(VertexAiTextEmbeddingOptions fromOptions) {
			if (fromOptions.getDimensions() != null) {
				this.options.setDimensions(fromOptions.getDimensions());
			}
			if (StringUtils.hasText(fromOptions.getModel())) {
				this.options.setModel(fromOptions.getModel());
			}
			if (fromOptions.getTaskType() != null) {
				this.options.setTaskType(fromOptions.getTaskType());
			}
			if (fromOptions.getAutoTruncate() != null) {
				this.options.setAutoTruncate(fromOptions.getAutoTruncate());
			}
			if (StringUtils.hasText(fromOptions.getTitle())) {
				this.options.setTitle(fromOptions.getTitle());
			}
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder model(VertexAiTextEmbeddingModelName model) {
			this.options.setModel(model.getName());
			return this;
		}

		public Builder taskType(TaskType taskType) {
			this.options.setTaskType(taskType);
			return this;
		}

		public Builder dimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public Builder title(String user) {
			this.options.setTitle(user);
			return this;
		}

		public Builder autoTruncate(Boolean autoTruncate) {
			this.options.setAutoTruncate(autoTruncate);
			return this;
		}

		public VertexAiTextEmbeddingOptions build() {
			return this.options;
		}

	}

}
