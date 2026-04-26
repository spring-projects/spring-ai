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

package org.springframework.ai.jina;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.scoring.ScoringOptions;

/**
 * Jina AI options.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JinaScoringOptions implements ScoringOptions {

	/** Default model name. */
	private static final String DEFAULT_MODEL = JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue();

	/** Model name. */
	@JsonProperty("model")
	private String modelName = DEFAULT_MODEL;

	/** Top K results. */
	@JsonProperty("top_n")
	private @Nullable Integer topKValue;

	/** Return documents flag. */
	@JsonProperty("return_documents")
	private @Nullable Boolean returnDocuments;

	/** Truncation flag. */
	@JsonProperty("truncation")
	private @Nullable Boolean truncation;

	/**
	 * Create builder.
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.modelName;
	}

	/**
	 * Set model.
	 * @param name model name
	 */
	public void setModel(final String name) {
		this.modelName = name;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topKValue;
	}

	/**
	 * Set top K.
	 * @param value value
	 */
	public void setTopK(final Integer value) {
		this.topKValue = value;
	}

	/**
	 * Get return documents.
	 * @return flag
	 */
	public @Nullable Boolean getReturnDocuments() {
		return this.returnDocuments;
	}

	/**
	 * Set return documents.
	 * @param enabled flag
	 */
	public void setReturnDocuments(final Boolean enabled) {
		this.returnDocuments = enabled;
	}

	/**
	 * Get truncation.
	 * @return flag
	 */
	public @Nullable Boolean getTruncation() {
		return this.truncation;
	}

	/**
	 * Set truncation.
	 * @param enabled flag
	 */
	public void setTruncation(final Boolean enabled) {
		this.truncation = enabled;
	}

	/** Builder class. */
	public static final class Builder {

		/** Options. */
		private final JinaScoringOptions options = new JinaScoringOptions();

		/**
		 * Set model.
		 * @param name model name
		 * @return builder
		 */
		public Builder model(final String name) {
			this.options.setModel(name);
			return this;
		}

		/**
		 * Set top K.
		 * @param value value
		 * @return builder
		 */
		public Builder topK(final Integer value) {
			this.options.setTopK(value);
			return this;
		}

		/**
		 * Set return documents.
		 * @param enabled flag
		 * @return builder
		 */
		public Builder returnDocuments(final Boolean enabled) {
			this.options.setReturnDocuments(enabled);
			return this;
		}

		/**
		 * Set truncation.
		 * @param enabled flag
		 * @return builder
		 */
		public Builder truncation(final Boolean enabled) {
			this.options.setTruncation(enabled);
			return this;
		}

		/**
		 * Build.
		 * @return options
		 */
		public JinaScoringOptions build() {
			return this.options;
		}

	}

}
