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

package org.springframework.ai.model.voyageai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties of the Voyage AI reranker. The reranker bean is created when
 * {@code spring.ai.voyageai.rerank.model} is set.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
@ConfigurationProperties(VoyageAiRerankProperties.CONFIG_PREFIX)
public class VoyageAiRerankProperties {

	public static final String CONFIG_PREFIX = "spring.ai.voyageai.rerank";

	/**
	 * The rerank model: {@code rerank-3}, {@code rerank-3-lite}, {@code rerank-2.5} or
	 * {@code rerank-2.5-lite}. Required, there is no default.
	 */
	private @Nullable String model;

	/**
	 * Keeps only the {@code top-k} most relevant documents. Left unset, all are kept.
	 */
	private @Nullable Integer topK;

	/**
	 * Whether over-long input is truncated rather than rejected. Left unset, Voyage AI
	 * decides.
	 */
	private @Nullable Boolean truncation;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public @Nullable Boolean getTruncation() {
		return this.truncation;
	}

	public void setTruncation(@Nullable Boolean truncation) {
		this.truncation = truncation;
	}

}
