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

package org.springframework.ai.rag.retrieval.join;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.util.Assert;

/**
 * Combines documents retrieved based on multiple queries and from multiple data sources
 * by concatenating them into a single collection of documents. In case of duplicate
 * documents, the first occurrence is kept. The score of each document is kept as is.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ConcatenationDocumentJoiner implements DocumentJoiner {

	private static final Logger logger = LoggerFactory.getLogger(ConcatenationDocumentJoiner.class);

	@Override
	public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
		Assert.notNull(documentsForQuery, "documentsForQuery cannot be null");
		Assert.noNullElements(documentsForQuery.keySet(), "documentsForQuery cannot contain null keys");
		Assert.noNullElements(documentsForQuery.values(), "documentsForQuery cannot contain null values");

		logger.debug("Joining documents by concatenation");

		return new ArrayList<>(documentsForQuery.values()
			.stream()
			.flatMap(List::stream)
			.flatMap(List::stream)
			.collect(Collectors.toMap(Document::getId, Function.identity(), (existing, duplicate) -> existing))
			.values());
	}

}
