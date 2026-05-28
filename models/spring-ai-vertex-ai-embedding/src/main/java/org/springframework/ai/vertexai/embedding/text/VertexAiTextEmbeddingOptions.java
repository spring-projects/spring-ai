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

package org.springframework.ai.vertexai.embedding.text;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.util.StringUtils;

/**
 * Options for the Vertex AI Text Embedding service.
 *
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
public class VertexAiTextEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_MODEL_NAME = VertexAiTextEmbeddingModelName.TEXT_EMBEDDING_004.getName();

	/**
	 * The embedding model name to use. Supported models are: text-embedding-004,
	 * text-multilingual-embedding-002 and multimodalembedding@001.
	 */
	private final @Nullable String model;

	// @formatter:off

	/**
	 * The intended downstream application to help the model produce better quality embeddings.
	 * Not all model versions support all task types.
	 */
	private final @Nullable TaskType taskType;

	/**
	 * The number of dimensions the resulting output embeddings should have.
	 * Supported for model version 004 and later. You can use this parameter to reduce the
	 * embedding size, for example, for storage optimization.
	 */
	private final @Nullable Integer dimensions;

	/**
	 * Optional title, only valid with task_type=RETRIEVAL_DOCUMENT.
	 */
	private final @Nullable String title;

	/**
	 * When set to true, input text will be truncated. When set to false, an error is returned
	 * if the input text is longer than the maximum length supported by the model. Defaults to true.
	 */
	private final @Nullable Boolean autoTruncate;

	protected VertexAiTextEmbeddingOptions(@Nullable String model, @Nullable TaskType taskType,
			@Nullable Integer dimensions, @Nullable String title, @Nullable Boolean autoTruncate) {
		this.model = model;
		if (StringUtils.hasText(title) && taskType != TaskType.RETRIEVAL_DOCUMENT) {
			throw new IllegalArgumentException("Title is only valid with task_type=RETRIEVAL_DOCUMENT");
		}
		this.taskType = (taskType != null ? taskType : TaskType.RETRIEVAL_DOCUMENT);
		this.dimensions = dimensions;
		this.title = title;
		this.autoTruncate = autoTruncate;
	}

	public static VertexAiTextEmbeddingOptions.Builder builder() {
		return new Builder();
	}


	// @formatter:on

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public @Nullable TaskType getTaskType() {
		return this.taskType;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public @Nullable String getTitle() {
		return this.title;
	}

	public @Nullable Boolean getAutoTruncate() {
		return this.autoTruncate;
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

	public static final class Builder {

		private @Nullable String model;

		private @Nullable TaskType taskType;

		private @Nullable Integer dimensions;

		private @Nullable String title;

		private @Nullable Boolean autoTruncate;

		public Builder() {
		}

		public Builder from(VertexAiTextEmbeddingOptions fromOptions) {
			if (fromOptions.getDimensions() != null) {
				this.dimensions = fromOptions.getDimensions();
			}
			if (StringUtils.hasText(fromOptions.getModel())) {
				this.model = fromOptions.getModel();
			}
			if (fromOptions.getTaskType() != null) {
				this.taskType = fromOptions.getTaskType();
			}
			if (fromOptions.getAutoTruncate() != null) {
				this.autoTruncate = fromOptions.getAutoTruncate();
			}
			if (StringUtils.hasText(fromOptions.getTitle())) {
				this.title = fromOptions.getTitle();
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder model(VertexAiTextEmbeddingModelName model) {
			this.model = model.getName();
			return this;
		}

		public Builder taskType(@Nullable TaskType taskType) {
			this.taskType = taskType;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public Builder title(@Nullable String user) {
			this.title = user;
			return this;
		}

		public Builder autoTruncate(@Nullable Boolean autoTruncate) {
			this.autoTruncate = autoTruncate;
			return this;
		}

		public VertexAiTextEmbeddingOptions build() {
			return new VertexAiTextEmbeddingOptions(this.model, this.taskType, this.dimensions, this.title,
					this.autoTruncate);
		}

	}

}
