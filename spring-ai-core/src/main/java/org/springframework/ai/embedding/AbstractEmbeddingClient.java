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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract implementation of the {@link EmbeddingClient} interface that provides
 * dimensions calculation caching.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractEmbeddingClient implements EmbeddingClient {

	private final AtomicInteger embeddingDimensions = new AtomicInteger(-1);

	@Override
	public int dimensions() {
		if (this.embeddingDimensions.get() < 0) {
			this.embeddingDimensions.set(EmbeddingUtil.dimensions(this, "Test"));
		}
		return this.embeddingDimensions.get();
	}

}
