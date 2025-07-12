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

import org.springframework.ai.vectorstore.VectorStoreBuilderCustomizer;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * A customizer interface for RedisVectorStore.Builder.
 * <p>
 * This allows users to apply additional configuration to a Redis-based VectorStore
 * without overriding the default auto-configuration.
 * <p>
 * For example, custom metadata fields can be added via this hook:
 *
 * <pre>{@code
 * &#64;Bean
 * public RedisVectorStoreBuilderCustomizer metadataCustomizer() {
 *     return builder -> builder.metadataFields(List.of(RedisVectorStore.MetadataField.tag("conversationId")));
 * }
 * }</pre>
 *
 * @author Dongha Koo
 * @see RedisVectorStore
 * @see VectorStore
 */
public interface RedisVectorStoreBuilderCustomizer extends VectorStoreBuilderCustomizer<RedisVectorStore.Builder> {

}
