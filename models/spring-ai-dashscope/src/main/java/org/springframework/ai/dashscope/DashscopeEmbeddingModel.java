/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.dashscope;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.dashscope.api.DashscopeApi;
import org.springframework.ai.dashscope.record.TokenUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.*;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Dashscope AI Embedding Client implementation
 *
 * @author Nottyjay Ji
 */
public class DashscopeEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(DashscopeEmbeddingModel.class);

	private final DashscopeEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final DashscopeApi dashscopeApi;

	private final MetadataMode metadataMode;

	/**
	 * Initializes a new instance of the Dashscope EmbeddingClient class.
	 * @param dashscopeApi - The Dashscope instance to use for making API requests.
	 */
	public DashscopeEmbeddingModel(DashscopeApi dashscopeApi) {
		this(dashscopeApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the Dashscope EmbeddingClient class.
	 * @param dashscopeApi - The Dashscope instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 */
	public DashscopeEmbeddingModel(DashscopeApi dashscopeApi, MetadataMode metadataMode) {
		this(dashscopeApi, metadataMode,
				DashscopeEmbeddingOptions.builder()
					.withModel(DashscopeApi.DEFAULT_EMBEDDING_MODEL)
					.withParameters(DashscopeEmbeddingOptions.EmbeddingParameter.builder()
						.withTextType(DashscopeEmbeddingOptions.EmbeddingParameter.EmbeddingTextType.QUERY)
						.build())
					.build());
	}

	/**
	 * Initializes a new instance of the Dashscope EmbeddingClient class.
	 * @param dashscopeApi - The Dashscope instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param embeddingOptions - The embedding options
	 */
	public DashscopeEmbeddingModel(DashscopeApi dashscopeApi, MetadataMode metadataMode,
			DashscopeEmbeddingOptions embeddingOptions) {
		this(dashscopeApi, metadataMode, embeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the Dashscope EmbeddingClient class.
	 * @param dashscopeApi - The Dashscope instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param embeddingOptions - The embedding options
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public DashscopeEmbeddingModel(DashscopeApi dashscopeApi, MetadataMode metadataMode,
			DashscopeEmbeddingOptions embeddingOptions, RetryTemplate retryTemplate) {
		Assert.notNull(dashscopeApi, "OpenAiService must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(embeddingOptions, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");

		this.dashscopeApi = dashscopeApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = embeddingOptions;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public List<Double> embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		return this.retryTemplate.execute(ctx -> {
			DashscopeApi.EmbeddingRequest embeddingRequest = new DashscopeApi.EmbeddingRequest(
					new DashscopeApi.EmbeddingTextList(request.getInstructions()), this.defaultOptions.getModel());
			ResponseEntity<DashscopeApi.DashscopeEmbeddingResponse> textEmbeddingResult = this.dashscopeApi
				.embeddings(embeddingRequest);
			if (textEmbeddingResult == null) {
				logger.warn("No embeddings returned for request: {}", request);
				return new EmbeddingResponse(List.of());
			}
			var metadata = generateResponseMetadata(this.defaultOptions.getModel(),
					textEmbeddingResult.getBody().usage());
			List<Embedding> embeddings = textEmbeddingResult.getBody()
				.output()
				.embeddings()
				.stream()
				.map(embedding -> new Embedding(embedding.embedding(), embedding.index()))
				.toList();
			return new EmbeddingResponse(embeddings, metadata);
		});
	}

	private EmbeddingResponseMetadata generateResponseMetadata(String model, TokenUsage usage) {
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.put("model", model);
		metadata.put("total-tokens", usage.totalTokens());
		return metadata;
	}

	public static DashScopeEmbeddingClientBuilder builder() {
		return new DashScopeEmbeddingClientBuilder();
	}

	/** Builder */
	public static class DashScopeEmbeddingClientBuilder {

		private DashscopeApi dashscopeApi;

		private MetadataMode metadataMode;

		private DashscopeEmbeddingOptions embeddingOptions;

		public DashScopeEmbeddingClientBuilder withDashscopeApi(DashscopeApi dashscopeApi) {
			this.dashscopeApi = dashscopeApi;
			return this;
		}

		public DashScopeEmbeddingClientBuilder withMetadataMode(MetadataMode metadataMode) {
			this.metadataMode = metadataMode;
			return this;
		}

		public DashScopeEmbeddingClientBuilder withEmbeddingOptions(DashscopeEmbeddingOptions embeddingOptions) {
			this.embeddingOptions = embeddingOptions;
			return this;
		}

		public DashscopeEmbeddingModel build() {
			if (embeddingOptions != null) {
				return new DashscopeEmbeddingModel(dashscopeApi, metadataMode, embeddingOptions);
			}
			else {
				if (metadataMode != null) {
					return new DashscopeEmbeddingModel(dashscopeApi, metadataMode);
				}
				else {
					return new DashscopeEmbeddingModel(dashscopeApi);
				}
			}
		}

	}

}
