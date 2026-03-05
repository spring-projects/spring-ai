/*
 * Copyright 2023-2025 the original author or authors.
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
 * The org.sf.ai.chat package represents the bounded context for the Chat Model within the
 * AI generative model domain. This package extends the core domain defined in
 * org.sf.ai.generative, providing implementations specific to chat-based generative AI
 * interactions.
 * <p>
 * In line with Domain-Driven Design principles, this package includes implementations of
 * entities and value objects specific to the chat context, such as ChatPrompt and
 * ChatResponse, adhering to the ubiquitous language of chat interactions in AI models.
 * <p>
 * This bounded context is designed to encapsulate all aspects of chat-based AI
 * functionalities, maintaining a clear boundary from other contexts within the AI domain.
 */

@NullMarked
package org.springframework.ai.chat.evaluation;

import org.jspecify.annotations.NullMarked;
