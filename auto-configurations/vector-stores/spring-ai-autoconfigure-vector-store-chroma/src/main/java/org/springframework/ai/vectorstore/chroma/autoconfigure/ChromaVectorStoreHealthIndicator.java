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

package org.springframework.ai.vectorstore.chroma.autoconfigure;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.util.Assert;

/**
 * {@link AbstractHealthIndicator Health indicator} for the Chroma vector store.
 * <p>
 * Verifies that the Chroma server is reachable by calling its heartbeat endpoint. Reports
 * the server's nanosecond heartbeat as part of the health details when {@code UP}.
 *
 * @author Prasad Anandappa
 * @since 2.1.0
 */
public class ChromaVectorStoreHealthIndicator extends AbstractHealthIndicator {

	private final ChromaApi chromaApi;

	public ChromaVectorStoreHealthIndicator(ChromaApi chromaApi) {
		super("Chroma health check failed");
		Assert.notNull(chromaApi, "ChromaApi must not be null");
		this.chromaApi = chromaApi;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		Long heartbeat = this.chromaApi.heartbeat();
		if (heartbeat == null) {
			builder.down().withDetail("reason", "Chroma heartbeat returned no response");
			return;
		}
		builder.up().withDetail("heartbeat", heartbeat);
	}

}
