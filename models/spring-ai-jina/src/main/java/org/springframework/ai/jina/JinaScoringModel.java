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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.jina.api.JinaScoringApi.RerankRequest;
import org.springframework.ai.jina.api.JinaScoringApi.RerankResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.scoring.ScoringModel;
import org.springframework.ai.scoring.ScoringOptions;
import org.springframework.ai.scoring.ScoringRequest;
import org.springframework.ai.scoring.ScoringResponse;
import org.springframework.ai.scoring.ScoringResult;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * Jina AI {@link ScoringModel}.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
public final class JinaScoringModel implements ScoringModel {

	/** Logger. */
	private final Logger log = LoggerFactory.getLogger(getClass());

	/** API client. */
	private final JinaScoringApi api;

	/** Retry template. */
	private final RetryTemplate retry;

	/** Options. */
	private final JinaScoringOptions options;

	/**
	 * Create model.
	 * @param apiParam api
	 * @param retryParam retry
	 * @param optionsParam options
	 */
	public JinaScoringModel(final JinaScoringApi apiParam, final RetryTemplate retryParam,
			final JinaScoringOptions optionsParam) {
		Assert.notNull(apiParam, "apiParam must not be null");
		Assert.notNull(retryParam, "retryParam must not be null");
		Assert.notNull(optionsParam, "optionsParam must not be null");
		this.api = apiParam;
		this.retry = retryParam;
		this.options = optionsParam;
	}

	@Override
	public ScoringResponse call(final ScoringRequest request) {
		Assert.notNull(request, "ScoringRequest must not be null");
		Assert.notEmpty(request.getInstructions(), "Documents must not be empty");

		return RetryUtils.execute(this.retry, () -> {
			JinaScoringOptions opts = mergeOptions(request.getOptions());
			List<Document> documents = request.getInstructions();
			List<String> texts = documents.stream().map(doc -> {
				String text = doc.getText();
				if (text == null) {
					throw new IllegalArgumentException("Document text null: " + doc.getId());
				}
				return text;
			}).toList();

			String modelName = opts.getModel();
			Assert.notNull(modelName, "Model name must not be null");
			RerankRequest apiReq = new RerankRequest(modelName, request.getQuery(), texts, opts.getTopK(), null,
					opts.getTruncation());

			ResponseEntity<RerankResponse> resp = this.api.rerank(apiReq);

			return convertResponse(resp, documents);
		});
	}

	@Override
	public ScoringResponse call(final String query, final List<Document> documents) {
		Assert.hasText(query, "Query must not be empty");
		Assert.notEmpty(documents, "Documents must not be empty");
		return this.call(new ScoringRequest(query, documents));
	}

	private JinaScoringOptions mergeOptions(final @Nullable ScoringOptions runtimeOptions) {
		if (runtimeOptions == null) {
			return this.options;
		}

		JinaScoringOptions jinaOpts;
		if (runtimeOptions instanceof JinaScoringOptions opts) {
			jinaOpts = opts;
		}
		else {
			JinaScoringOptions.Builder optBuilder = JinaScoringOptions.builder();
			Integer topK = runtimeOptions.getTopK();
			if (topK != null) {
				optBuilder.topK(topK);
			}
			jinaOpts = optBuilder.build();
		}

		return ModelOptionsUtils.merge(jinaOpts, this.options, JinaScoringOptions.class);
	}

	private ScoringResponse convertResponse(final ResponseEntity<RerankResponse> responseEntity,
			final List<Document> docs) {

		RerankResponse body = responseEntity.getBody();
		if (body == null) {
			this.log.warn("No response");
			return new ScoringResponse(List.of());
		}

		return new ScoringResponse(body.results().stream().map(res -> {
			Document doc = docs.get(res.index());
			Document scored = doc.mutate().score(res.relevanceScore()).build();
			return new ScoringResult(scored);
		}).toList());
	}

	/**
	 * Create builder.
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Builder class. */
	public static final class Builder {

		/** API client. */
		private @Nullable JinaScoringApi api;

		/** Retry template. */
		private RetryTemplate retry = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		/** Options. */
		private JinaScoringOptions options = JinaScoringOptions.builder().build();

		/**
		 * Set api.
		 * @param apiParam api
		 * @return builder
		 */
		public Builder jinaScoringApi(final JinaScoringApi apiParam) {
			this.api = apiParam;
			return this;
		}

		/**
		 * Set retry.
		 * @param retryParam retry
		 * @return builder
		 */
		public Builder retryTemplate(final RetryTemplate retryParam) {
			this.retry = retryParam;
			return this;
		}

		/**
		 * Set options.
		 * @param optionsParam options
		 * @return builder
		 */
		public Builder options(final JinaScoringOptions optionsParam) {
			this.options = optionsParam;
			return this;
		}

		/**
		 * Build.
		 * @return model
		 */
		public JinaScoringModel build() {
			Assert.state(this.api != null, "api must not be null");
			return new JinaScoringModel(this.api, this.retry, this.options);
		}

	}

}
