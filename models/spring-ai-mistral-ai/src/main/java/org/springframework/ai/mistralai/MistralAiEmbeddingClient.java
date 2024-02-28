/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.mistralai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.*;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.MistralAiApiException;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

/**
 * @author Ricken Bazolo
 * @since 0.8.1
 */
public class MistralAiEmbeddingClient extends AbstractEmbeddingClient {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MistralAiEmbeddingOptions defaultOptions;

	private final MetadataMode metadataMode;

	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(MistralAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				log.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
			};
		})
		.build();

	public MistralAiEmbeddingClient(MistralAiApi mistralAiApi) {
		this(mistralAiApi, MetadataMode.EMBED);
	}

	public MistralAiEmbeddingClient(MistralAiApi mistralAiApi, MetadataMode metadataMode) {
		this(mistralAiApi, metadataMode,
				MistralAiEmbeddingOptions.builder().withModel(MistralAiApi.EmbeddingModel.EMBED.getValue()).build());
	}

	public MistralAiEmbeddingClient(MistralAiApi mistralAiApi, MistralAiEmbeddingOptions options) {
		this(mistralAiApi, MetadataMode.EMBED, options);
	}

	public MistralAiEmbeddingClient(MistralAiApi mistralAiApi, MetadataMode metadataMode,
			MistralAiEmbeddingOptions options) {
		Assert.notNull(mistralAiApi, "MistralAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");

		this.mistralAiApi = mistralAiApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
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

			var metadata = generateResponseMetadata(apiEmbeddingResponse.model(), apiEmbeddingResponse.usage());

			var embeddings = apiEmbeddingResponse.data()
				.stream()
				.map(e -> new Embedding(e.embedding(), e.index()))
				.toList();

			return new EmbeddingResponse(embeddings, metadata);

		});
	}

	@Override
	public List<Double> embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	private EmbeddingResponseMetadata generateResponseMetadata(String model, MistralAiApi.Usage usage) {
		var metadata = new EmbeddingResponseMetadata();
		metadata.put("model", model);
		metadata.put("prompt-tokens", usage.promptTokens());
		metadata.put("total-tokens", usage.totalTokens());
		return metadata;
	}

}
