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
package org.springframework.ai.zhipuai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ZhiPuAI Embedding Client implementation.
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class ZhiPuAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(ZhiPuAiEmbeddingModel.class);

	private final ZhiPuAiEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ZhiPuAiApi zhiPuAiApi;

	private final MetadataMode metadataMode;

	/**
	 * Constructor for the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi) {
		this(zhiPuAiApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode) {
		this(zhiPuAiApi, metadataMode,
				ZhiPuAiEmbeddingOptions.builder().withModel(ZhiPuAiApi.DEFAULT_EMBEDDING_MODEL).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param zhiPuAiEmbeddingOptions The options for ZhiPuAI embedding.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode,
			ZhiPuAiEmbeddingOptions zhiPuAiEmbeddingOptions) {
		this(zhiPuAiApi, metadataMode, zhiPuAiEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi - The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for ZhiPuAI embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode, ZhiPuAiEmbeddingOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(zhiPuAiApi, "ZhiPuAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");

		this.zhiPuAiApi = zhiPuAiApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public List<Double> embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@SuppressWarnings("unchecked")
	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		return this.retryTemplate.execute(ctx -> {
			Assert.notEmpty(request.getInstructions(), "At least one text is required!");
			if (request.getInstructions().size() != 1) {
				logger.warn(
						"ZhiPu Embedding does not support batch embedding. Will make multiple API calls to embed(Document)");
			}

			List<List<Double>> embeddingList = new ArrayList<>();
			for (String inputContent : request.getInstructions()) {
				var apiRequest = createZhiPuEmbeddingRequest(inputContent, request.getOptions());
				ZhiPuAiApi.EmbeddingList<ZhiPuAiApi.Embedding> response = this.zhiPuAiApi.embeddings(apiRequest)
					.getBody();
				if (response == null || response.data() == null || response.data().isEmpty()) {
					logger.warn("No embeddings returned for input: {}", inputContent);
					embeddingList.add(List.of());
				}
				else {
					embeddingList.add(response.data().get(0).embedding());
				}
			}
			var indexCounter = new AtomicInteger(0);
			List<Embedding> embeddings = embeddingList.stream()
				.map(e -> new Embedding(e, indexCounter.getAndIncrement()))
				.toList();
			return new EmbeddingResponse(embeddings);
		});
	}

	private ZhiPuAiApi.EmbeddingRequest<String> createZhiPuEmbeddingRequest(String text,
			EmbeddingOptions requestOptions) {
		ZhiPuAiApi.EmbeddingRequest<String> apiRequest = (this.defaultOptions != null)
				? new ZhiPuAiApi.EmbeddingRequest<>(text, this.defaultOptions.getModel())
				: new ZhiPuAiApi.EmbeddingRequest<>(text, ZhiPuAiApi.DEFAULT_EMBEDDING_MODEL);

		if (requestOptions != null && !EmbeddingOptions.EMPTY.equals(requestOptions)) {
			apiRequest = ModelOptionsUtils.merge(requestOptions, apiRequest, ZhiPuAiApi.EmbeddingRequest.class);
		}
		return apiRequest;
	}

}
