/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.vectorstore.observation;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.util.Assert;

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class VectorStoreObservationContext extends Observation.Context {

	public enum Operation {

		/**
		 * VectorStore delete operation.
		 */
		ADD("add"),
		/**
		 * VectorStore add operation.
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

	// SEARCH
	private SearchRequest queryRequest;

	private List<Document> queryResponse;

	// COMMON
	private final String databaseSystem;

	private int dimensions = -1;

	private String similarityMetric = "";

	private String collectionName = "";

	private String namespace = "";

	private String fieldName = "";

	private String indexName = "";

	private final String operationName;

	public VectorStoreObservationContext(String databaseSystem, String operationName) {
		Assert.hasText(databaseSystem, "databaseSystem cannot be null or empty");
		Assert.hasText(operationName, "operationName cannot be null or empty");
		this.databaseSystem = databaseSystem;
		this.operationName = operationName;
	}

	public SearchRequest getQueryRequest() {
		return this.queryRequest;
	}

	public void setQueryRequest(SearchRequest request) {
		this.queryRequest = request;
	}

	public List<Document> getQueryResponse() {
		return this.queryResponse;
	}

	public void setQueryResponse(List<Document> documents) {
		this.queryResponse = documents;
	}

	public String getDatabaseSystem() {
		return this.databaseSystem;
	}

	public int getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}

	public String getSimilarityMetric() {
		return this.similarityMetric;
	}

	public void setSimilarityMetric(String similarityMetric) {
		this.similarityMetric = similarityMetric;
	}

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getNamespace() {
		return this.namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getFieldName() {
		return this.fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getOperationName() {
		return this.operationName;
	}

	public static Builder builder(String databaseSystem, String operationName) {
		return new Builder(databaseSystem, operationName);
	}

	public static Builder builder(String databaseSystem, Operation operation) {
		return builder(databaseSystem, operation.value);
	}

	public static class Builder {

		private VectorStoreObservationContext context;

		public Builder(String databaseSystem, String operationName) {
			this.context = new VectorStoreObservationContext(databaseSystem, operationName);
		}

		public Builder withQueryRequest(SearchRequest request) {
			this.context.setQueryRequest(request);
			return this;
		}

		public Builder withQueryResponse(List<Document> documents) {
			this.context.setQueryResponse(documents);
			return this;
		}

		public Builder withDimensions(int dimensions) {
			this.context.setDimensions(dimensions);
			return this;
		}

		public Builder withSimilarityMetric(String similarityMetric) {
			this.context.setSimilarityMetric(similarityMetric);
			return this;
		}

		public Builder withCollectionName(String collectionName) {
			this.context.setCollectionName(collectionName);
			return this;
		}

		public Builder withNamespace(String namespace) {
			this.context.setNamespace(namespace);
			return this;
		}

		public Builder withFieldName(String fieldName) {
			this.context.setFieldName(fieldName);
			return this;
		}

		public Builder withIndexName(String indexName) {
			this.context.setIndexName(indexName);
			return this;
		}

		public VectorStoreObservationContext build() {
			return this.context;
		}

	}

}