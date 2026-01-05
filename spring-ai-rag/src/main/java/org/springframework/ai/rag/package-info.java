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
 * This package contains the core interfaces and classes supporting Retrieval Augmented
 * Generation flows.
 * <p>
 * It's inspired by the Modular RAG Architecture and provides the necessary building
 * blocks to define and execute RAG flows.
 *
 * @see <a href="http://export.arxiv.org/abs/2407.21059">arXiv:2407.21059</a>
 * @see <a href="https://export.arxiv.org/abs/2312.10997">arXiv:2312.10997</a>
 * @see <a href="https://export.arxiv.org/abs/2410.20878">arXiv:2410.20878</a>
 */
@NullMarked
package org.springframework.ai.rag;

import org.jspecify.annotations.NullMarked;
