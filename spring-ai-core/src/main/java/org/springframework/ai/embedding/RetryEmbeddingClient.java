/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.embedding;

import java.util.List;

import org.springframework.ai.CommonUtils;
import org.springframework.ai.document.Document;
import org.springframework.retry.support.RetryTemplate;

/**
 * The RetryEmbeddingClient is a {@link EmbeddingClient} decorator that provides an
 * ability to automatically re-invoke the failed embed operations according to
 * pre-configured retry policies. This is helpful transient errors such as a momentary
 * network glitch.
 *
 * @author Christian Tzolov
 */
public class RetryEmbeddingClient implements EmbeddingClient {

	private final RetryTemplate retryTemplate;

	private final EmbeddingClient delegate;

	public RetryEmbeddingClient(EmbeddingClient delegate) {
		this(CommonUtils.DEFAULT_RETRY_TEMPLATE, delegate);
	}

	public RetryEmbeddingClient(RetryTemplate retryTemplate, EmbeddingClient delegate) {
		this.retryTemplate = retryTemplate;
		this.delegate = delegate;
	}

	@Override
	public List<Double> embed(String text) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.embed(text);
		});
	}

	@Override
	public List<Double> embed(Document document) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.embed(document);
		});
	}

	@Override
	public List<List<Double>> embed(List<String> texts) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.embed(texts);
		});
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.embedForResponse(texts);
		});
	}

}
