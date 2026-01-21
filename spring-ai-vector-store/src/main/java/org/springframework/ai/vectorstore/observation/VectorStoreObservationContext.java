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

package org.springframework.ai.vectorstore.observation;

import java.util.List;

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for vector store operations.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public class VectorStoreObservationContext extends Observation.Context {

	private final String databaseSystem;

	// COMMON

	private final String operationName;

	private @Nullable String collectionName;

	private @Nullable Integer dimensions;

	private @Nullable String fieldName;

	private @Nullable String namespace;

	private @Nullable String similarityMetric;

	private @Nullable SearchRequest queryRequest;

	// SEARCH

	private @Nullable List<Document> queryResponse;

	public VectorStoreObservationContext(String databaseSystem, String operationName) {
		Assert.hasText(databaseSystem, "databaseSystem cannot be null or empty");
		Assert.hasText(operationName, "operationName cannot be null or empty");
		this.databaseSystem = databaseSystem;
		this.operationName = operationName;
	}

	public static Builder builder(String databaseSystem, String operationName) {
		return new Builder(databaseSystem, operationName);
	}

	public static Builder builder(String databaseSystem, Operation operation) {
		return builder(databaseSystem, operation.value);
	}

	public String getDatabaseSystem() {
		return this.databaseSystem;
	}

	public String getOperationName() {
		return this.operationName;
	}

	public @Nullable String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(@Nullable String collectionName) {
		this.collectionName = collectionName;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	public @Nullable String getFieldName() {
		return this.fieldName;
	}

	public void setFieldName(@Nullable String fieldName) {
		this.fieldName = fieldName;
	}

	public @Nullable String getNamespace() {
		return this.namespace;
	}

	public void setNamespace(@Nullable String namespace) {
		this.namespace = namespace;
	}

	public @Nullable String getSimilarityMetric() {
		return this.similarityMetric;
	}

	public void setSimilarityMetric(@Nullable String similarityMetric) {
		this.similarityMetric = similarityMetric;
	}

	public @Nullable SearchRequest getQueryRequest() {
		return this.queryRequest;
	}

	public void setQueryRequest(@Nullable SearchRequest queryRequest) {
		this.queryRequest = queryRequest;
	}

	public @Nullable List<Document> getQueryResponse() {
		return this.queryResponse;
	}

	public void setQueryResponse(@Nullable List<Document> queryResponse) {
		this.queryResponse = queryResponse;
	}

	public enum Operation {

		/**
		 * VectorStore add operation.
		 */
		ADD("add"),
		/**
		 * VectorStore delete operation.
		 */
		DELETE("delete"),
		/**
		 * VectorStore similarity search operation.
		 */
		QUERY("query");

		public final String value;

		Operation(String value) {
			this.value = value;
		}

		public String value() {
			return this.value;
		}

	}

	public static final class Builder {

		private final VectorStoreObservationContext context;

		public Builder(String databaseSystem, String operationName) {
			this.context = new VectorStoreObservationContext(databaseSystem, operationName);
		}

		public Builder collectionName(String collectionName) {
			this.context.setCollectionName(collectionName);
			return this;
		}

		public Builder dimensions(Integer dimensions) {
			this.context.setDimensions(dimensions);
			return this;
		}

		public Builder fieldName(@Nullable String fieldName) {
			this.context.setFieldName(fieldName);
			return this;
		}

		public Builder namespace(String namespace) {
			this.context.setNamespace(namespace);
			return this;
		}

		public Builder queryRequest(SearchRequest request) {
			this.context.setQueryRequest(request);
			return this;
		}

		public Builder queryResponse(List<Document> documents) {
			this.context.setQueryResponse(documents);
			return this;
		}

		public Builder similarityMetric(String similarityMetric) {
			this.context.setSimilarityMetric(similarityMetric);
			return this;
		}

		public VectorStoreObservationContext build() {
			return this.context;
		}

	}

}
