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

/**
 * A customizer interface for customizing a VectorStore builder instance.
 *
 * @param <T> the type of VectorStore builder
 * @author Dongha Koo
 * @since 1.0.0
 */
public interface VectorStoreBuilderCustomizer<T extends VectorStore.Builder<T>> {
	void customize(T builder);
}
