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

import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * Default conventions to populate observations for vector store operations.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultVectorStoreObservationConvention implements VectorStoreObservationConvention {

	public static final String DEFAULT_NAME = "db.vector.client.operation";

	private static final KeyValue COLLECTION_NAME_NONE = KeyValue.of(HighCardinalityKeyNames.DB_COLLECTION_NAME,
			KeyValue.NONE_VALUE);

	private static final KeyValue DIMENSIONS_NONE = KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT,
			KeyValue.NONE_VALUE);

	private static final KeyValue METADATA_FILTER_NONE = KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_FILTER,
			KeyValue.NONE_VALUE);

	private static final KeyValue FIELD_NAME_NONE = KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME,
			KeyValue.NONE_VALUE);

	private static final KeyValue NAMESPACE_NONE = KeyValue.of(HighCardinalityKeyNames.DB_NAMESPACE,
			KeyValue.NONE_VALUE);

	private static final KeyValue QUERY_CONTENT_NONE = KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT,
			KeyValue.NONE_VALUE);

	private static final KeyValue SIMILARITY_METRIC_NONE = KeyValue
		.of(HighCardinalityKeyNames.DB_VECTOR_SIMILARITY_METRIC, KeyValue.NONE_VALUE);

	private static final KeyValue SIMILARITY_THRESHOLD_NONE = KeyValue
		.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD, KeyValue.NONE_VALUE);

	private static final KeyValue TOP_K_NONE = KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K,
			KeyValue.NONE_VALUE);

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
	@Nullable
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
		return KeyValues.of(collectionName(context), dimensions(context), fieldName(context), metadataFilter(context),
				namespace(context), queryContent(context), similarityMetric(context), similarityThreshold(context),
				topK(context));
	}

	protected KeyValue collectionName(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getCollectionName())) {
			return KeyValue.of(HighCardinalityKeyNames.DB_COLLECTION_NAME, context.getCollectionName());
		}
		return COLLECTION_NAME_NONE;
	}

	protected KeyValue dimensions(VectorStoreObservationContext context) {
		if (context.getDimensions() != null && context.getDimensions() > 0) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT, "" + context.getDimensions());
		}
		return DIMENSIONS_NONE;
	}

	protected KeyValue fieldName(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getFieldName())) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME, context.getFieldName());
		}
		return FIELD_NAME_NONE;
	}

	protected KeyValue metadataFilter(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getFilterExpression() != null) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_FILTER,
					context.getQueryRequest().getFilterExpression().toString());
		}
		return METADATA_FILTER_NONE;
	}

	protected KeyValue namespace(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getNamespace())) {
			return KeyValue.of(HighCardinalityKeyNames.DB_NAMESPACE, context.getNamespace());
		}
		return NAMESPACE_NONE;
	}

	protected KeyValue queryContent(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && StringUtils.hasText(context.getQueryRequest().getQuery())) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT, context.getQueryRequest().getQuery());
		}
		return QUERY_CONTENT_NONE;
	}

	protected KeyValue similarityMetric(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getSimilarityMetric())) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_SIMILARITY_METRIC, context.getSimilarityMetric());
		}
		return SIMILARITY_METRIC_NONE;
	}

	protected KeyValue similarityThreshold(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getSimilarityThreshold() >= 0) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD,
					String.valueOf(context.getQueryRequest().getSimilarityThreshold()));
		}
		return SIMILARITY_THRESHOLD_NONE;
	}

	protected KeyValue topK(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getTopK() > 0) {
			return KeyValue.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K, "" + context.getQueryRequest().getTopK());
		}
		return TOP_K_NONE;
	}

}
