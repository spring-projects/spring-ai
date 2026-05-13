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

package org.springframework.ai.vectorstore.observation.autoconfigure;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.health.contributor.HealthIndicator} for a
 * {@link VectorStore}. Performs a lightweight similarity search with {@code topK=0} to
 * verify that the store is reachable and responding without retrieving any results.
 * <p>
 * Note: the probe issues a query through the store's configured embedding pipeline. If
 * the underlying embedding model is unavailable, this indicator will report {@code DOWN}
 * even if the vector store itself is healthy.
 *
 * @author Surya Teja Gorre
 * @since 2.0.0
 */
public class VectorStoreHealthIndicator extends AbstractHealthIndicator {

	private final VectorStore vectorStore;

	public VectorStoreHealthIndicator(VectorStore vectorStore) {
		super("VectorStore health check failed");
		Assert.notNull(vectorStore, "VectorStore must not be null");
		this.vectorStore = vectorStore;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		this.vectorStore.similaritySearch(SearchRequest.builder().query("health").topK(0).build());
		String name = this.vectorStore.getName();
		builder.up()
			.withDetail("vectorStore", StringUtils.hasText(name) ? name : this.vectorStore.getClass().getSimpleName());
	}

}
