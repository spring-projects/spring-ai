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
 * Callback interface that can be implemented by Spring beans wishing to customize a
 * VectorStore builder instance.
 *
 * <p>
 * Customizers are applied in order (if ordered) before the VectorStore is built, and
 * allow for programmatic configuration of builder options without overriding the entire
 * auto-configuration.
 *
 * <p>
 * This is commonly used for setting metadata fields or observation configurations in a
 * type-safe and non-invasive manner.
 *
 * <p>
 * Example usage: <pre>{@code
 * &#64;Bean
 * public RedisVectorStoreBuilderCustomizer customizer() {
 *     return builder -> builder.metadataFields(
 *         List.of(RedisVectorStore.MetadataField.tag("conversationId")));
 * }
 * }</pre>
 *
 * @param <T> the type of VectorStore builder to customize
 * @author Dongha Koo
 * @see VectorStore
 * @see VectorStore.Builder
 */
public interface VectorStoreBuilderCustomizer<T extends VectorStore.Builder<T>> {

	void customize(T builder);

}
