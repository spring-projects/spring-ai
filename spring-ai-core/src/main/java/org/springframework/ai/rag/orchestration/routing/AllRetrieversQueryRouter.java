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

package org.springframework.ai.rag.orchestration.routing;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.util.Assert;

/**
 * Routes a query to all the defined document retrievers.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class AllRetrieversQueryRouter implements QueryRouter {

	private static final Logger logger = LoggerFactory.getLogger(AllRetrieversQueryRouter.class);

	private final List<DocumentRetriever> documentRetrievers;

	public AllRetrieversQueryRouter(List<DocumentRetriever> documentRetrievers) {
		Assert.notEmpty(documentRetrievers, "documentRetrievers cannot be null or empty");
		Assert.noNullElements(documentRetrievers, "documentRetrievers cannot contain null elements");
		this.documentRetrievers = documentRetrievers;
	}

	@Override
	public List<DocumentRetriever> route(Query query) {
		Assert.notNull(query, "query cannot be null");
		logger.debug("Routing query to all document retrievers");
		return this.documentRetrievers;
	}

	public static Builder builder() {
		return new Builder();
	}

	public final static class Builder {

		private List<DocumentRetriever> documentRetrievers;

		private Builder() {
		}

		public Builder documentRetrievers(DocumentRetriever... documentRetrievers) {
			this.documentRetrievers = Arrays.asList(documentRetrievers);
			return this;
		}

		public Builder documentRetrievers(List<DocumentRetriever> documentRetrievers) {
			this.documentRetrievers = documentRetrievers;
			return this;
		}

		public AllRetrieversQueryRouter build() {
			return new AllRetrieversQueryRouter(this.documentRetrievers);
		}

	}

}
