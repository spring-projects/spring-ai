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
package org.springframework.ai.qianfan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.ai.qianfan.api.QianFanApi.EmbeddingList;
import org.springframework.ai.qianfan.metadata.QianFanUsage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.List;

/**
 * QianFan Embedding Client implementation.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0
 */
public class QianFanEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(QianFanEmbeddingModel.class);

	private final QianFanEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final QianFanApi qianFanApi;

	private final MetadataMode metadataMode;

	/**
	 * Constructor for the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi) {
		this(qianFanApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode) {
		this(qianFanApi, metadataMode,
				QianFanEmbeddingOptions.builder().withModel(QianFanApi.DEFAULT_EMBEDDING_MODEL).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param qianFanEmbeddingOptions The options for QianFan embedding.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode,
			QianFanEmbeddingOptions qianFanEmbeddingOptions) {
		this(qianFanApi, metadataMode, qianFanEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi - The QianFanApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for QianFan embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode, QianFanEmbeddingOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(qianFanApi, "QianFanApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");

		this.qianFanApi = qianFanApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
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
			QianFanApi.EmbeddingRequest apiRequest = (this.defaultOptions != null)
					? new QianFanApi.EmbeddingRequest(request.getInstructions(), this.defaultOptions.getModel(),
							this.defaultOptions.getUser())
					: new QianFanApi.EmbeddingRequest(request.getInstructions());

			if (request.getOptions() != null && !EmbeddingOptions.EMPTY.equals(request.getOptions())) {
				apiRequest = ModelOptionsUtils.merge(request.getOptions(), apiRequest,
						QianFanApi.EmbeddingRequest.class);
			}

			EmbeddingList apiEmbeddingResponse = this.qianFanApi.embeddings(apiRequest).getBody();

			if (apiEmbeddingResponse == null) {
				logger.warn("No embeddings returned for request: {}", request);
				return new EmbeddingResponse(List.of());
			}

			if (apiEmbeddingResponse.errorNsg() != null) {
				logger.error("Error message returned for request: {}", apiEmbeddingResponse.errorNsg());
				throw new RuntimeException("Embedding failed: error code:" + apiEmbeddingResponse.errorCode()
						+ ", message:" + apiEmbeddingResponse.errorNsg());
			}

			var metadata = new EmbeddingResponseMetadata(apiEmbeddingResponse.model(),
					QianFanUsage.from(apiEmbeddingResponse.usage()));

			List<Embedding> embeddings = apiEmbeddingResponse.data()
				.stream()
				.map(e -> new Embedding(e.embedding(), e.index()))
				.toList();

			return new EmbeddingResponse(embeddings, metadata);
		});
	}

}
