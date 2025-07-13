/*
 * Copyright 2025 the original author or authors.
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

/**
 * Callback interface for customizing {@link VectorStore.Builder} instances.
 * <p>
 * Implemented by Spring beans that wish to configure {@code VectorStore} builders
 * before they are constructed. Customizers are applied in declaration order
 * (or priority order if {@link org.springframework.core.Ordered} is implemented),
 * allowing incremental builder configuration while preserving auto-configuration.
 *
 * <h3>Typical Use Cases</h3>
 * <ul>
 *   <li>Adding custom metadata fields to vector stores</li>
 *   <li>Configuring observation behaviors</li>
 * </ul>
 *
 * @param <T> the specific type of {@link VectorStore.Builder} being customized
 * @author Pengfei Lan
 * @see VectorStore
 * @see VectorStore.Builder
 * @see org.springframework.core.Ordered
 */
@FunctionalInterface
public interface VectorStoreBuilderCustomizer<T extends VectorStore.Builder<T>> {

	/**
	 * Customizes the given {@link VectorStore.Builder} instance.
	 * @param builder the builder to configure (never {@code null})
	 */
	void customize(T builder);
}
