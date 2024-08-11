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

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class VectorStoreObservationContext extends Observation.Context {

	// DELETE
	private List<String> deleteRequest;

	// ADD
	private List<Document> addRequest;

	// SEARCH
	private SearchRequest searchRequest;

	private List<Document> searchResponse;

	// COMMON
	private final String databaseSystem;

	private int dimensions = -1;

	private String similarityMetric = "";

	private String collectionName = "";

	private String namespace = "";

	private String model = "";

	private String fieldName = "";

	private String indexName = "";

	private String operationName;

	public VectorStoreObservationContext(String databaseSystem) {
		this.databaseSystem = databaseSystem;
	}

	public List<String> getDeleteRequest() {
		return this.deleteRequest;
	}

	public void setDeleteRequest(List<String> deleteRequest) {
		this.deleteRequest = deleteRequest;
	}

	public List<Document> getAddRequest() {
		return this.addRequest;
	}

	public void setAddRequest(List<Document> documents) {
		this.addRequest = documents;
	}

	public SearchRequest getSearchRequest() {
		return this.searchRequest;
	}

	public void setSearchRequest(SearchRequest request) {
		this.searchRequest = request;
	}

	public List<Document> getSearchResponse() {
		return this.searchResponse;
	}

	public void setSearchResponse(List<Document> documents) {
		this.searchResponse = documents;
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

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
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

	public void setOperationName(String operationName) {
		this.operationName = operationName;
	}

	public static Builder builder(String databaseSystem) {
		return new Builder(databaseSystem);
	}

	public static class Builder {

		private VectorStoreObservationContext context;

		public Builder(String databaseSystem) {
			this.context = new VectorStoreObservationContext(databaseSystem);
		}

		public Builder withDeleteRequest(List<String> deleteRequest) {
			this.context.setDeleteRequest(deleteRequest);
			return this;
		}

		public Builder withAddRequest(List<Document> documents) {
			this.context.setAddRequest(documents);
			return this;
		}

		public Builder withSearchRequest(SearchRequest request) {
			this.context.setSearchRequest(request);
			return this;
		}

		public Builder withSearchResponse(List<Document> documents) {
			this.context.setSearchResponse(documents);
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

		public Builder withModel(String model) {
			this.context.setModel(model);
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

		public Builder withOperationName(String operationName) {
			this.context.setOperationName(operationName);
			return this;
		}

		public VectorStoreObservationContext build() {
			return this.context;
		}

	}

}