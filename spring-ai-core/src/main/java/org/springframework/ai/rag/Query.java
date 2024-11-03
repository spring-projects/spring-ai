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

package org.springframework.ai.rag;

import org.springframework.util.Assert;

/**
 * Represents a query in the context of a Retrieval Augmented Generation (RAG) flow.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record Query(String text) {
	public Query {
		Assert.hasText(text, "text cannot be null or empty");
	}
}
