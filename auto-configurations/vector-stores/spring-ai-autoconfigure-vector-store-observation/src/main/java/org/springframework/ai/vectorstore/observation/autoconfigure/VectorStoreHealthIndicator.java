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
 * {@link VectorStore}. Issues a {@code topK=1} similarity search probe to verify the
 * store is reachable and responding. The single result is discarded.
 * <p>
 * <strong>Embedding model dependency:</strong> most {@link VectorStore} implementations
 * route {@code similaritySearch} through the configured embedding pipeline, which means
 * every health-check poll issues a remote call to the embedding model (OpenAI, Azure
 * OpenAI, Bedrock, etc.). Consequences:
 * <ul>
 * <li>If the embedding model is unavailable or rate-limited this indicator reports
 * {@code DOWN} even when the vector store itself is healthy.</li>
 * <li>Each poll incurs an embedding API call that may be billed and counted against
 * rate-limit quotas. In a multi-replica deployment with frequent Actuator polling, this
 * generates significant background API traffic. Adjust
 * {@code management.health.vectorStore.enabled} and the poll interval accordingly.</li>
 * </ul>
 * To suppress health checks for a specific store, set
 * {@code management.health.vectorStore.enabled=false}.
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
		this.vectorStore.similaritySearch(SearchRequest.builder().query("health").topK(1).build());
		String name = this.vectorStore.getName();
		builder.up()
			.withDetail("vectorStore", StringUtils.hasText(name) ? name : this.vectorStore.getClass().getSimpleName());
	}

}
