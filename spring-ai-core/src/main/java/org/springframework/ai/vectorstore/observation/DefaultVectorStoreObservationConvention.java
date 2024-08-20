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

import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class DefaultVectorStoreObservationConvention implements VectorStoreObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.vector.store";

	private static final String VECTOR_STORE_SPRING_AI_KIND = "vector_store";

	private static final KeyValue DIMENSIONS_NONE = KeyValue.of(HighCardinalityKeyNames.DIMENSIONS,
			KeyValue.NONE_VALUE);

	private static final KeyValue QUERY_NONE = KeyValue.of(HighCardinalityKeyNames.QUERY, KeyValue.NONE_VALUE);

	private static final KeyValue METADATA_FILTER_NONE = KeyValue.of(HighCardinalityKeyNames.QUERY_METADATA_FILTER,
			KeyValue.NONE_VALUE);

	private static final KeyValue TOP_K_NONE = KeyValue.of(HighCardinalityKeyNames.TOP_K, KeyValue.NONE_VALUE);

	private static final KeyValue SIMILARITY_THRESHOLD_NONE = KeyValue.of(HighCardinalityKeyNames.SIMILARITY_THRESHOLD,
			KeyValue.NONE_VALUE);

	private static final KeyValue SIMILARITY_METRIC_NONE = KeyValue.of(HighCardinalityKeyNames.SIMILARITY_METRIC,
			KeyValue.NONE_VALUE);

	private static final KeyValue COLLECTION_NAME_NONE = KeyValue.of(HighCardinalityKeyNames.COLLECTION_NAME,
			KeyValue.NONE_VALUE);

	private static final KeyValue NAMESPACE_NONE = KeyValue.of(HighCardinalityKeyNames.NAMESPACE, KeyValue.NONE_VALUE);

	private static final KeyValue FIELD_NAME_NONE = KeyValue.of(HighCardinalityKeyNames.FIELD_NAME,
			KeyValue.NONE_VALUE);

	private static final KeyValue INDEX_NAME_NONE = KeyValue.of(HighCardinalityKeyNames.INDEX_NAME,
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
		return "%s %s %s".formatted(VECTOR_STORE_SPRING_AI_KIND, context.getDatabaseSystem(),
				context.getOperationName());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(VectorStoreObservationContext context) {
		return KeyValues.of(springAiKind(), dbSystem(context), dbOperationName(context));
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(VectorStoreObservationContext context) {
		return KeyValues.of(query(context), metadataFilter(context), topK(context), dimensions(context),
				similarityMetric(context), collectionName(context), namespace(context), fieldName(context),
				indexName(context), similarityThreshold(context));
	}

	protected KeyValue springAiKind() {
		return KeyValue.of(LowCardinalityKeyNames.SPRING_AI_KIND, VECTOR_STORE_SPRING_AI_KIND);
	}

	protected KeyValue dbSystem(VectorStoreObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.DB_SYSTEM, context.getDatabaseSystem());
	}

	protected KeyValue dbOperationName(VectorStoreObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.DB_OPERATION_NAME, context.getOperationName());
	}

	protected KeyValue dimensions(VectorStoreObservationContext context) {
		if (context.getDimensions() > 0) {
			return KeyValue.of(HighCardinalityKeyNames.DIMENSIONS, "" + context.getDimensions());
		}
		return DIMENSIONS_NONE;
	}

	protected KeyValue query(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && StringUtils.hasText(context.getQueryRequest().getQuery())) {
			return KeyValue.of(HighCardinalityKeyNames.QUERY, "" + context.getQueryRequest().getQuery());
		}
		return QUERY_NONE;
	}

	protected KeyValue metadataFilter(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getFilterExpression() != null) {
			return KeyValue.of(HighCardinalityKeyNames.QUERY_METADATA_FILTER,
					"" + context.getQueryRequest().getFilterExpression().toString());
		}
		return METADATA_FILTER_NONE;
	}

	protected KeyValue topK(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getTopK() > 0) {
			return KeyValue.of(HighCardinalityKeyNames.TOP_K, "" + context.getQueryRequest().getTopK());
		}
		return TOP_K_NONE;
	}

	protected KeyValue similarityThreshold(VectorStoreObservationContext context) {
		if (context.getQueryRequest() != null && context.getQueryRequest().getSimilarityThreshold() >= 0) {
			return KeyValue.of(HighCardinalityKeyNames.SIMILARITY_THRESHOLD,
					"" + context.getQueryRequest().getSimilarityThreshold());
		}
		return SIMILARITY_THRESHOLD_NONE;
	}

	protected KeyValue similarityMetric(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getSimilarityMetric())) {
			return KeyValue.of(HighCardinalityKeyNames.SIMILARITY_METRIC, context.getSimilarityMetric());
		}
		return SIMILARITY_METRIC_NONE;
	}

	protected KeyValue collectionName(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getCollectionName())) {
			return KeyValue.of(HighCardinalityKeyNames.COLLECTION_NAME, context.getCollectionName());
		}
		return COLLECTION_NAME_NONE;
	}

	protected KeyValue namespace(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getNamespace())) {
			return KeyValue.of(HighCardinalityKeyNames.NAMESPACE, context.getNamespace());
		}
		return NAMESPACE_NONE;
	}

	protected KeyValue fieldName(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getFieldName())) {
			return KeyValue.of(HighCardinalityKeyNames.FIELD_NAME, context.getFieldName());
		}
		return FIELD_NAME_NONE;
	}

	protected KeyValue indexName(VectorStoreObservationContext context) {
		if (StringUtils.hasText(context.getIndexName())) {
			return KeyValue.of(HighCardinalityKeyNames.INDEX_NAME, context.getIndexName());
		}
		return INDEX_NAME_NONE;
	}

}