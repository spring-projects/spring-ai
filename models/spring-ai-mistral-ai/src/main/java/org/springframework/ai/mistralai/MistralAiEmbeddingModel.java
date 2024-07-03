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
package org.springframework.ai.mistralai;

import java.util.List;

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
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.metadata.MistralAiUsage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * @author Ricken Bazolo
 * @author Thomas Vitale
 * @since 0.8.1
 */
public class MistralAiEmbeddingModel extends AbstractEmbeddingModel {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MistralAiEmbeddingOptions defaultOptions;

	private final MetadataMode metadataMode;

	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate;

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi) {
		this(mistralAiApi, MetadataMode.EMBED);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MetadataMode metadataMode) {
		this(mistralAiApi, metadataMode,
				MistralAiEmbeddingOptions.builder().withModel(MistralAiApi.EmbeddingModel.EMBED.getValue()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MistralAiEmbeddingOptions options) {
		this(mistralAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MetadataMode metadataMode,
			MistralAiEmbeddingOptions options, RetryTemplate retryTemplate) {
		Assert.notNull(mistralAiApi, "MistralAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");

		this.mistralAiApi = mistralAiApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	@SuppressWarnings("unchecked")
	public EmbeddingResponse call(EmbeddingRequest request) {
		return this.retryTemplate.execute(ctx -> {

			var apiRequest = (this.defaultOptions != null)
					? new MistralAiApi.EmbeddingRequest<>(request.getInstructions(), this.defaultOptions.getModel(),
							this.defaultOptions.getEncodingFormat())
					: new MistralAiApi.EmbeddingRequest<>(request.getInstructions(),
							MistralAiApi.EmbeddingModel.EMBED.getValue());

			if (request.getOptions() != null && !EmbeddingOptions.EMPTY.equals(request.getOptions())) {
				apiRequest = ModelOptionsUtils.merge(request.getOptions(), apiRequest,
						MistralAiApi.EmbeddingRequest.class);
			}

			var apiEmbeddingResponse = this.mistralAiApi.embeddings(apiRequest).getBody();

			if (apiEmbeddingResponse == null) {
				log.warn("No embeddings returned for request: {}", request);
				return new EmbeddingResponse(List.of());
			}

			var metadata = new EmbeddingResponseMetadata(apiEmbeddingResponse.model(),
					MistralAiUsage.from(apiEmbeddingResponse.usage()));

			var embeddings = apiEmbeddingResponse.data()
				.stream()
				.map(e -> new Embedding(e.embedding(), e.index()))
				.toList();

			return new EmbeddingResponse(embeddings, metadata);

		});
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

}
