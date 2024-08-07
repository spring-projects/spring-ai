/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.base.WeaviateErrorMessage;
import io.weaviate.client.v1.batch.model.BatchDeleteResponse;
import io.weaviate.client.v1.batch.model.ObjectGetResponse;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLError;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.argument.WhereArgument;
import io.weaviate.client.v1.graphql.query.builder.GetBuilder;
import io.weaviate.client.v1.graphql.query.builder.GetBuilder.GetBuilderBuilder;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.graphql.query.fields.Fields;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig.ConsistentLevel;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * A VectorStore implementation backed by Weaviate vector database.
 *
 * Note: You can assign arbitrary metadata fields with your Documents. Later will be
 * persisted and managed as Document fields. But only the metadata keys listed in
 * {@link WeaviateVectorStore#filterMetadataFields} can be used for similarity search
 * expression filters.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Soby Chacko
 */
public class WeaviateVectorStore implements VectorStore {

	public static final String DOCUMENT_METADATA_DISTANCE_KEY_NAME = "distance";

	private static final String METADATA_FIELD_PREFIX = "meta_";

	private static final String CONTENT_FIELD_NAME = "content";

	private static final String METADATA_FIELD_NAME = "metadata";

	private static final String ADDITIONAL_FIELD_NAME = "_additional";

	private static final String ADDITIONAL_ID_FIELD_NAME = "id";

	private static final String ADDITIONAL_CERTAINTY_FIELD_NAME = "certainty";

	private static final String ADDITIONAL_VECTOR_FIELD_NAME = "vector";

	private final EmbeddingModel embeddingModel;

	private final WeaviateClient weaviateClient;

	private final ConsistentLevel consistencyLevel;

	private final String weaviateObjectClass;

	/**
	 * List of metadata fields (as field name and type) that can be used in similarity
	 * search query filter expressions. The {@link Document#getMetadata()} can contain
	 * arbitrary number of metadata entries, but only the fields listed here can be used
	 * in the search filter expressions.
	 *
	 * If new entries are added ot the filterMetadataFields the affected documents must be
	 * (re)updated.
	 */
	private final List<MetadataField> filterMetadataFields;

	/**
	 * List of weaviate field to retrieve whey performing similarity search.
	 */
	private final Field[] weaviateSimilaritySearchFields;

	/**
	 * Converts the generic {@link Filter.Expression} into, native, Weaviate filter
	 * expressions.
	 */
	private final WeaviateFilterExpressionConverter filterExpressionConverter;

	/**
	 * Used to serialize/deserialize the document metadata when stored/retrieved from the
	 * weaviate vector store.
	 */
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Configuration class for the WeaviateVectorStore.
	 */
	public static final class WeaviateVectorStoreConfig {

		public record MetadataField(String name, Type type) {
			public enum Type {

				TEXT, NUMBER, BOOLEAN

			}

			public static MetadataField text(String name) {
				return new MetadataField(name, Type.TEXT);
			}

			public static MetadataField number(String name) {
				return new MetadataField(name, Type.NUMBER);
			}

			public static MetadataField bool(String name) {
				return new MetadataField(name, Type.BOOLEAN);
			}

		}

		/**
		 * https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-consistency-strategies
		 */
		public enum ConsistentLevel {

			/**
			 * Write must receive an acknowledgement from at least one replica node. This
			 * is the fastest (most available), but least consistent option.
			 */
			ONE,

			/**
			 * Write must receive an acknowledgement from at least QUORUM replica nodes.
			 * QUORUM is calculated as n / 2 + 1, where n is the number of replicas.
			 */
			QUORUM,

			/**
			 * Write must receive an acknowledgement from all replica nodes. This is the
			 * most consistent, but 'slowest'.
			 */
			ALL

		}

		private final String weaviateObjectClass;

		private final ConsistentLevel consistencyLevel;

		/**
		 * Known metadata fields to add as a fields to the Weaviate schema. You can add
		 * arbitrary metadata with your documents but only the metadata fields listed here
		 * can be used in the expression filters.
		 */
		private final List<MetadataField> filterMetadataFields;

		private final Map<String, String> headers;

		/**
		 * Constructor using the builder.
		 * @param builder The configuration builder.
		 */
		public WeaviateVectorStoreConfig(Builder builder) {
			this.weaviateObjectClass = builder.objectClass;
			this.consistencyLevel = builder.consistencyLevel;
			this.filterMetadataFields = builder.filterMetadataFields;
			this.headers = builder.headers;
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * {@return the default config}
		 */
		public static WeaviateVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		public static class Builder {

			private String objectClass = "SpringAiWeaviate";

			private ConsistentLevel consistencyLevel = WeaviateVectorStoreConfig.ConsistentLevel.ONE;

			private List<MetadataField> filterMetadataFields = List.of();

			private Map<String, String> headers = Map.of();

			private Builder() {
			}

			/**
			 * Weaviate known, filterable metadata fields.
			 * @param filterMetadataFields known metadata fields to use.
			 * @return this builder.
			 */
			public Builder withFilterableMetadataFields(List<MetadataField> filterMetadataFields) {
				Assert.notNull(filterMetadataFields, "The filterMetadataFields can not be null.");
				this.filterMetadataFields = filterMetadataFields;
				return this;
			}

			/**
			 * Weaviate config headers.
			 * @param headers config headers to use.
			 * @return this builder.
			 */
			public Builder withHeaders(Map<String, String> headers) {
				Assert.notNull(headers, "The headers can not be null.");
				this.headers = headers;
				return this;
			}

			/**
			 * Weaviate objectClass.
			 * @param objectClass objectClass to use.
			 * @return this builder.
			 */
			public Builder withObjectClass(String objectClass) {
				Assert.hasText(objectClass, "The objectClass can not be empty.");
				this.objectClass = objectClass;
				return this;
			}

			/**
			 * Weaviate consistencyLevel.
			 * @param consistencyLevel consistencyLevel to use.
			 * @return this builder.
			 */
			public Builder withConsistencyLevel(ConsistentLevel consistencyLevel) {
				Assert.notNull(consistencyLevel, "The consistencyLevel can not be null.");
				this.consistencyLevel = consistencyLevel;
				return this;
			}

			/**
			 * {@return the immutable configuration}
			 */
			public WeaviateVectorStoreConfig build() {
				return new WeaviateVectorStoreConfig(this);
			}

		}

	}

	/**
	 * Constructs a new WeaviateVectorStore.
	 * @param vectorStoreConfig The configuration for the store.
	 * @param embeddingModel The client for embedding operations.
	 */
	public WeaviateVectorStore(WeaviateVectorStoreConfig vectorStoreConfig, EmbeddingModel embeddingModel,
			WeaviateClient weaviateClient) {
		Assert.notNull(vectorStoreConfig, "WeaviateVectorStoreConfig must not be null");
		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");

		this.embeddingModel = embeddingModel;
		this.consistencyLevel = vectorStoreConfig.consistencyLevel;
		this.weaviateObjectClass = vectorStoreConfig.weaviateObjectClass;
		this.filterMetadataFields = vectorStoreConfig.filterMetadataFields;
		this.filterExpressionConverter = new WeaviateFilterExpressionConverter(
				this.filterMetadataFields.stream().map(MetadataField::name).toList());
		this.weaviateClient = weaviateClient;
		this.weaviateSimilaritySearchFields = buildWeaviateSimilaritySearchFields();
	}

	private Field[] buildWeaviateSimilaritySearchFields() {

		List<Field> searchWeaviateFieldList = new ArrayList<>();

		searchWeaviateFieldList.add(Field.builder().name(CONTENT_FIELD_NAME).build());
		searchWeaviateFieldList.add(Field.builder().name(METADATA_FIELD_NAME).build());
		searchWeaviateFieldList.addAll(this.filterMetadataFields.stream()
			.map(mf -> Field.builder().name(METADATA_FIELD_PREFIX + mf.name()).build())
			.toList());
		searchWeaviateFieldList.add(Field.builder()
			.name(ADDITIONAL_FIELD_NAME)
			// https://weaviate.io/developers/weaviate/api/graphql/get#additional-properties--metadata
			.fields(Field.builder().name(ADDITIONAL_ID_FIELD_NAME).build(),
					Field.builder().name(ADDITIONAL_CERTAINTY_FIELD_NAME).build(),
					Field.builder().name(ADDITIONAL_VECTOR_FIELD_NAME).build())
			.build());

		return searchWeaviateFieldList.toArray(new Field[0]);
	}

	@Override
	public void add(List<Document> documents) {

		if (CollectionUtils.isEmpty(documents)) {
			return;
		}

		List<WeaviateObject> weaviateObjects = documents.stream().map(this::toWeaviateObject).toList();

		Result<ObjectGetResponse[]> response = this.weaviateClient.batch()
			.objectsBatcher()
			.withObjects(weaviateObjects.toArray(new WeaviateObject[0]))
			.withConsistencyLevel(this.consistencyLevel.name())
			.run();

		List<String> errorMessages = new ArrayList<>();

		if (response.hasErrors()) {
			errorMessages.add(response.getError()
				.getMessages()
				.stream()
				.map(wm -> wm.getMessage())
				.collect(Collectors.joining(System.lineSeparator())));
			throw new RuntimeException("Failed to add documents because: \n" + errorMessages);
		}

		if (response.getResult() != null) {
			for (var r : response.getResult()) {
				if (r.getResult() != null && r.getResult().getErrors() != null) {
					var error = r.getResult().getErrors();
					errorMessages.add(error.getError()
						.stream()
						.map(e -> e.getMessage())
						.collect(Collectors.joining(System.lineSeparator())));
				}
			}
		}

		if (!CollectionUtils.isEmpty(errorMessages)) {
			throw new RuntimeException("Failed to add documents because: \n" + errorMessages);
		}
	}

	private WeaviateObject toWeaviateObject(Document document) {

		if (CollectionUtils.isEmpty(document.getEmbedding())) {
			List<Double> embedding = this.embeddingModel.embed(document);
			document.setEmbedding(embedding);
		}

		// https://weaviate.io/developers/weaviate/config-refs/datatypes
		Map<String, Object> fields = new HashMap<>();
		fields.put(CONTENT_FIELD_NAME, document.getContent());
		try {
			String metadataString = this.objectMapper.writeValueAsString(document.getMetadata());
			fields.put(METADATA_FIELD_NAME, metadataString);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize the Document metadata: " + document.getContent());
		}

		// Add the filterable metadata fields as top level fields, allowing filler
		// expressions on them.
		for (MetadataField mf : this.filterMetadataFields) {
			if (document.getMetadata().containsKey(mf.name())) {
				fields.put(METADATA_FIELD_PREFIX + mf.name(), document.getMetadata().get(mf.name()));
			}
		}

		return WeaviateObject.builder()
			.className(this.weaviateObjectClass)
			.id(document.getId())
			.vector(toFloatArray(document.getEmbedding()))
			.properties(fields)
			.build();
	}

	@Override
	public Optional<Boolean> delete(List<String> documentIds) {

		Result<BatchDeleteResponse> result = this.weaviateClient.batch()
			.objectsBatchDeleter()
			.withClassName(this.weaviateObjectClass)
			.withConsistencyLevel(this.consistencyLevel.name())
			.withWhere(WhereFilter.builder()
				.path("id")
				.operator(Operator.ContainsAny)
				.valueString(documentIds.toArray(new String[0]))
				.build())
			.run();

		if (result.hasErrors()) {
			String errorMessages = result.getError()
				.getMessages()
				.stream()
				.map(wm -> wm.getMessage())
				.collect(Collectors.joining(","));
			throw new RuntimeException("Failed to delete documents because: \n" + errorMessages);
		}

		return Optional.of(!result.hasErrors());
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		Float[] embedding = toFloatArray(this.embeddingModel.embed(request.getQuery()));

		GetBuilder.GetBuilderBuilder builder = GetBuilder.builder();

		GetBuilderBuilder queryBuilder = builder.className(this.weaviateObjectClass)
			.withNearVectorFilter(NearVectorArgument.builder()
				.vector(embedding)
				.certainty((float) request.getSimilarityThreshold())
				.build())
			.limit(request.getTopK())
			.withWhereFilter(WhereArgument.builder().build()) // adds an empty 'where:{}'
																// placeholder.
			.fields(Fields.builder().fields(this.weaviateSimilaritySearchFields).build());

		String graphQLQuery = queryBuilder.build().buildQuery();

		if (request.hasFilterExpression()) {
			// replace the empty 'where:{}' placeholder with real filter.
			String filter = this.filterExpressionConverter.convertExpression(request.getFilterExpression());
			graphQLQuery = graphQLQuery.replace("where:{}", String.format("where:{%s}", filter));
		}
		else {
			// remove the empty 'where:{}' placeholder.
			graphQLQuery = graphQLQuery.replace("where:{}", "");
		}

		Result<GraphQLResponse> result = this.weaviateClient.graphQL().raw().withQuery(graphQLQuery).run();

		if (result.hasErrors()) {
			throw new IllegalArgumentException(result.getError()
				.getMessages()
				.stream()
				.map(WeaviateErrorMessage::getMessage)
				.collect(Collectors.joining(System.lineSeparator())));
		}

		GraphQLError[] errors = result.getResult().getErrors();
		if (errors != null && errors.length > 0) {
			throw new IllegalArgumentException(Arrays.stream(errors)
				.map(GraphQLError::getMessage)
				.collect(Collectors.joining(System.lineSeparator())));
		}

		@SuppressWarnings("unchecked")
		Optional<Map.Entry<String, Map<?, ?>>> resGetPart = ((Map<String, Map<?, ?>>) result.getResult().getData())
			.entrySet()
			.stream()
			.findFirst();
		if (!resGetPart.isPresent()) {
			return List.of();
		}

		Optional<?> resItemsPart = resGetPart.get().getValue().entrySet().stream().findFirst();
		if (!resItemsPart.isPresent()) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		List<Map<String, ?>> resItems = ((Map.Entry<String, List<Map<String, ?>>>) resItemsPart.get()).getValue();

		return resItems.stream().map(this::toDocument).toList();
	}

	@SuppressWarnings("unchecked")
	private Document toDocument(Map<String, ?> item) {

		// Additional (System)
		Map<String, ?> additional = (Map<String, ?>) item.get(ADDITIONAL_FIELD_NAME);
		double certainty = (Double) additional.get(ADDITIONAL_CERTAINTY_FIELD_NAME);
		String id = (String) additional.get(ADDITIONAL_ID_FIELD_NAME);
		List<Double> embedding = ((List<Double>) additional.get(ADDITIONAL_VECTOR_FIELD_NAME)).stream().toList();

		// Metadata
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DOCUMENT_METADATA_DISTANCE_KEY_NAME, 1 - certainty);

		try {
			String metadataJson = (String) item.get(METADATA_FIELD_NAME);
			if (StringUtils.hasText(metadataJson)) {
				metadata.putAll(this.objectMapper.readValue(metadataJson, Map.class));
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		// Content
		String content = (String) item.get(CONTENT_FIELD_NAME);

		var document = new Document(id, content, metadata);
		document.setEmbedding(embedding);

		return document;
	}

	/**
	 * Converts a list of doubles to an array of floats.
	 * @param doubleList The list of doubles.
	 * @return The converted array of floats.
	 */
	private Float[] toFloatArray(List<Double> doubleList) {
		return doubleList.stream().map(Number::floatValue).toList().toArray(new Float[0]);
	}

}