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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.util.StringUtils;

/**
 * Default conventions to populate observations for vector store operations.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultVectorStoreObservationConvention implements VectorStoreObservationConvention {

	public static final String DEFAULT_NAME = "db.vector.client.operation";

	private final String name;

	public DefaultVectorStoreObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultVectorStoreObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getContextualName(VectorStoreObservationContext context) {
		return "%s %s".formatted(context.getDatabaseSystem(), context.getOperationName());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(VectorStoreObservationContext context) {
		return KeyValues.of(springAiKind(), dbSystem(context), dbOperationName(context));
	}

	protected KeyValue springAiKind() {
		return KeyValue.of(LowCardinalityKeyNames.SPRING_AI_KIND, SpringAiKind.VECTOR_STORE.value());
	}

	protected KeyValue dbSystem(VectorStoreObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.DB_SYSTEM, context.getDatabaseSystem());
	}

	protected KeyValue dbOperationName(VectorStoreObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.DB_OPERATION_NAME, context.getOperationName());
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(VectorStoreObservationContext context) {
		var keyValues = KeyValues.empty();
		keyValues = collectionName(keyValues, context);
		keyValues = dimensions(keyValues, context);
		keyValues = fieldName(keyValues, context);
		keyValues = metadataFilter(keyValues, context);
		keyValues = namespace(keyValues, context);
		keyValues = queryContent(keyValues, context);
		keyValues = similarityMetric(keyValues, context);
		keyValues = similarityThreshold(keyValues, context);
		keyValues = topK(keyValues, context);
		return keyValues;
	}

	protected KeyValues collectionName(KeyValues keyValues, VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getCollectionName())) {
			return keyValues.and(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), context.getCollectionName());
		}
		return keyValues;
	}

	protected KeyValues dimensions(KeyValues keyValues, VectorStoreObservationContext context) {
		if (context.getDimensions() != null && context.getDimensions() > 0) {
			return keyValues.and(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(),
					"" + context.getDimensions());
		}
		return keyValues;
	}

	protected KeyValues fieldName(KeyValues keyValues, VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getFieldName())) {
			return keyValues.and(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString(), context.getFieldName());
		}
		return keyValues;
	}

	protected KeyValues metadataFilter(KeyValues keyValues, VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getFilterExpression() != null) {
			return keyValues.and(HighCardinalityKeyNames.DB_VECTOR_QUERY_FILTER.asString(),
					context.getQueryRequest().getFilterExpression().toString());
		}
		return keyValues;
	}

	protected KeyValues namespace(KeyValues keyValues, VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getNamespace())) {
			return keyValues.and(HighCardinalityKeyNames.DB_NAMESPACE.asString(), context.getNamespace());
		}
		return keyValues;
	}

	protected KeyValues queryContent(KeyValues keyValues, VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && StringUtils.hasText(context.getQueryRequest().getQuery())) {
			return keyValues.and(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString(),
					context.getQueryRequest().getQuery());
		}
		return keyValues;
	}

	protected KeyValues similarityMetric(KeyValues keyValues, VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getSimilarityMetric())) {
			return keyValues.and(HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString(),
					context.getSimilarityMetric());
		}
		return keyValues;
	}

	protected KeyValues similarityThreshold(KeyValues keyValues, VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getSimilarityThreshold() >= 0) {
			return keyValues.and(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString(),
					String.valueOf(context.getQueryRequest().getSimilarityThreshold()));
		}
		return keyValues;
	}

	protected KeyValues topK(KeyValues keyValues, VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getTopK() > 0) {
			return keyValues.and(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString(),
					"" + context.getQueryRequest().getTopK());
		}
		return keyValues;
	}

}
