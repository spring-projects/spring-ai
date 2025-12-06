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

package org.springframework.ai.vectorstore.redis;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreBuilderCustomizer;

/**
 * Customizer interface for {@link RedisVectorStore.Builder} configuration.
 * <p>
 * Allows customization of Redis-based {@link VectorStore} configuration while preserving
 * default auto-configuration. Implementations can add additional settings or modify
 * existing ones without overriding the entire configuration.
 *
 * <h3>Usage Example</h3> The following example shows how to add custom metadata fields:
 * <pre>{@code
 * &#64;Bean
 * public RedisVectorStoreBuilderCustomizer metadataCustomizer() {
 *     return builder -> builder.metadataFields(
 *         List.of(RedisVectorStore.MetadataField.tag("conversationId"))
 *     );
 * }
 * }</pre>
 *
 * @author Pengfei Lan
 * @see RedisVectorStore
 * @see VectorStore
 */
public interface RedisVectorStoreBuilderCustomizer extends VectorStoreBuilderCustomizer<RedisVectorStore.Builder> {

}
