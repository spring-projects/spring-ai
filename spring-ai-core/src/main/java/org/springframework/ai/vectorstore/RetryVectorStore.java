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

package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.retry.support.RetryTemplate;

/**
 * The {@link RetryVectorStore} is a {@link VectorStore} decorator that provides an
 * ability to automatically re-invoke the failed vector store operations according to
 * pre-configured retry policies. This is helpful transient errors such as a momentary
 * network glitch.
 *
 * @author Christian Tzolov
 */
public class RetryVectorStore implements VectorStore {

	private final RetryTemplate retryTemplate;

	private final VectorStore delegate;

	public RetryVectorStore(RetryTemplate retryTemplate, VectorStore delegate) {
		this.retryTemplate = retryTemplate;
		this.delegate = delegate;
	}

	@Override
	public void add(List<Document> documents) {
		this.retryTemplate.execute(ctx -> {
			this.delegate.add(documents);
			return null;
		});

	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.delete(idList);
		});
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.similaritySearch(request);
		});
	}

}
