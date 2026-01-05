/*
 * Copyright 2023-2024 the original author or authors.
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

/**
 * Provides interfaces and implementations for working with vector databases in Spring AI.
 * <p>
 * Vector databases store embeddings (numerical vector representations) of data along with
 * the original content and metadata, enabling similarity search operations. This package
 * contains two primary interfaces:
 * <ul>
 * <li>{@link org.springframework.ai.vectorstore.VectorStoreRetriever} - A read-only
 * functional interface that provides similarity search capabilities for retrieving
 * documents from a vector store. This interface follows the principle of least privilege
 * by exposing only retrieval operations.</li>
 * <li>{@link org.springframework.ai.vectorstore.VectorStore} - Extends
 * VectorStoreRetriever and adds mutation operations (add, delete) for managing documents
 * in a vector store. This interface provides complete access to vector database
 * functionality.</li>
 * </ul>
 * <p>
 * The package also includes supporting classes such as:
 * <ul>
 * <li>{@link org.springframework.ai.vectorstore.SearchRequest} - Configures similarity
 * search parameters including query text, result limits, similarity thresholds, and
 * metadata filters.</li>
 * <li>{@link org.springframework.ai.vectorstore.filter.Filter} - Provides filtering
 * capabilities for metadata-based document selection (located in the filter
 * subpackage).</li>
 * </ul>
 * <p>
 * This package is designed to support Retrieval Augmented Generation (RAG) applications
 * by providing a clean separation between read and write operations, allowing components
 * to access only the functionality they need.
 *
 * @see org.springframework.ai.vectorstore.VectorStoreRetriever
 * @see org.springframework.ai.vectorstore.VectorStore
 * @see org.springframework.ai.vectorstore.SearchRequest
 * @see org.springframework.ai.vectorstore.filter.Filter
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
@NullMarked
package org.springframework.ai.vectorstore;

import org.jspecify.annotations.NullMarked;
