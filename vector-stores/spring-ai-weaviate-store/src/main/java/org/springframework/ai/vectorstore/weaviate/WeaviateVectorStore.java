/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.weaviate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.base.WeaviateErrorMessage;
import io.weaviate.client.v1.batch.model.BatchDeleteResponse;
import io.weaviate.client.v1.batch.model.ObjectGetResponse;
import io.weaviate.client.v1.batch.model.ObjectsGetResponseAO2Result;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * A vector store implementation that stores and retrieves vectors in a Weaviate database.
 *
 * Note: You can assign arbitrary metadata fields with your Documents. Later will be
 * persisted and managed as Document fields. But only the metadata keys listed in
 * {@link WeaviateVectorStore#filterMetadataFields} can be used for similarity search
 * expression filters.
 *
 * <p>
 * Example usage with builder:
 * </p>
 * <pre>{@code
 * // Create the vector store with builder
 * WeaviateVectorStore vectorStore = WeaviateVectorStore.builder(weaviateClient, embeddingModel)
 *     .options(options)                     	  // Optional: use custom options
 *     .consistencyLevel(ConsistentLevel.QUORUM)  // Optional: Set consistency level (default: ONE)
 *     .filterMetadataFields(List.of(             // Optional: Configure filterable metadata fields
 *         MetadataField.text("country"),
 *         MetadataField.number("year")
 *     ))
 *     .build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @since 1.0.0
 */
public class WeaviateVectorStore extends AbstractObservationVectorStore {

	private static final Logger logger = LoggerFactory.getLogger(WeaviateVectorStore.class);

	private static final String METADATA_FIELD_NAME = "metadata";

	private static final String ADDITIONAL_FIELD_NAME = "_additional";

	private static final String ADDITIONAL_ID_FIELD_NAME = "id";

	private static final String ADDITIONAL_CERTAINTY_FIELD_NAME = "certainty";

	private static final String ADDITIONAL_VECTOR_FIELD_NAME = "vector";

	private final WeaviateClient weaviateClient;

	private final WeaviateVectorStoreOptions options;

	private final ConsistentLevel consistencyLevel;

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
	 * Protected constructor for creating a WeaviateVectorStore instance using the builder
	 * pattern. This constructor initializes the vector store with the configured settings
	 * from the builder and performs necessary validations.
	 * @param builder the {@link Builder} containing all configuration settings
	 * @throws IllegalArgumentException if the weaviateClient is null
	 * @see Builder
	 * @since 1.0.0
	 */
	protected WeaviateVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.weaviateClient, "WeaviateClient must not be null");

		this.options = builder.options;

		this.weaviateClient = builder.weaviateClient;
		this.consistencyLevel = builder.consistencyLevel;
		this.filterMetadataFields = builder.filterMetadataFields;
		this.filterExpressionConverter = new WeaviateFilterExpressionConverter(
				this.filterMetadataFields.stream().map(MetadataField::name).toList(),
				this.options.getMetaFieldPrefix());
		this.weaviateSimilaritySearchFields = buildWeaviateSimilaritySearchFields();
	}

	/**
	 * Creates a new WeaviateBuilder instance. This is the recommended way to instantiate
	 * a WeaviateVectorStore.
	 * @return a new WeaviateBuilder instance
	 */
	public static Builder builder(WeaviateClient weaviateClient, EmbeddingModel embeddingModel) {
		return new Builder(weaviateClient, embeddingModel);
	}

	private Field[] buildWeaviateSimilaritySearchFields() {

		List<Field> searchWeaviateFieldList = new ArrayList<>();

		searchWeaviateFieldList.add(Field.builder().name(this.options.getContentFieldName()).build());
		searchWeaviateFieldList.add(Field.builder().name(METADATA_FIELD_NAME).build());
		searchWeaviateFieldList.addAll(this.filterMetadataFields.stream()
			.map(mf -> Field.builder().name(this.options.getMetaFieldPrefix() + mf.name()).build())
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
	public void doAdd(List<Document> documents) {

		if (CollectionUtils.isEmpty(documents)) {
			return;
		}

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		List<WeaviateObject> weaviateObjects = documents.stream()
			.map(document -> toWeaviateObject(document, documents, embeddings))
			.toList();

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
				.map(WeaviateErrorMessage::getMessage)
				.collect(Collectors.joining(System.lineSeparator())));
			throw new RuntimeException("Failed to add documents because: \n" + errorMessages);
		}

		if (response.getResult() != null) {
			for (var r : response.getResult()) {
				if (r.getResult() != null && r.getResult().getErrors() != null) {
					var error = r.getResult().getErrors();
					errorMessages.add(error.getError()
						.stream()
						.map(ObjectsGetResponseAO2Result.ErrorItem::getMessage)
						.collect(Collectors.joining(System.lineSeparator())));
				}
			}
		}

		if (!CollectionUtils.isEmpty(errorMessages)) {
			throw new RuntimeException("Failed to add documents because: \n" + errorMessages);
		}
	}

	private WeaviateObject toWeaviateObject(Document document, List<Document> documents, List<float[]> embeddings) {

		// https://weaviate.io/developers/weaviate/config-refs/datatypes
		Map<String, Object> fields = new HashMap<>();
		fields.put(this.options.getContentFieldName(), document.getText());
		try {
			String metadataString = this.objectMapper.writeValueAsString(document.getMetadata());
			fields.put(METADATA_FIELD_NAME, metadataString);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize the Document metadata: " + document.getText());
		}

		// Add the filterable metadata fields as top level fields, allowing filler
		// expressions on them.
		for (MetadataField mf : this.filterMetadataFields) {
			if (document.getMetadata().containsKey(mf.name())) {
				fields.put(this.options.getMetaFieldPrefix() + mf.name(), document.getMetadata().get(mf.name()));
			}
		}

		return WeaviateObject.builder()
			.className(this.options.getObjectClass())
			.id(document.getId())
			.vector(EmbeddingUtils.toFloatArray(embeddings.get(documents.indexOf(document))))
			.properties(fields)
			.build();
	}

	@Override
	public void doDelete(List<String> documentIds) {

		Result<BatchDeleteResponse> result = this.weaviateClient.batch()
			.objectsBatchDeleter()
			.withClassName(this.options.getObjectClass())
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
				.map(WeaviateErrorMessage::getMessage)
				.collect(Collectors.joining(","));
			throw new RuntimeException("Failed to delete documents because: \n" + errorMessages);
		}
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			// Use similarity search with empty query to find documents matching the
			// filter
			SearchRequest searchRequest = SearchRequest.builder()
				.query("") // empty query since we only want filter matches
				.filterExpression(filterExpression)
				.topK(10000) // large enough to get all matches
				.similarityThresholdAll()
				.build();

			List<Document> matchingDocs = similaritySearch(searchRequest);

			if (!matchingDocs.isEmpty()) {
				List<String> idsToDelete = matchingDocs.stream().map(Document::getId).collect(Collectors.toList());

				delete(idsToDelete);

				logger.debug("Deleted {} documents matching filter expression", idsToDelete.size());
			}
			else {
				logger.debug("No documents found matching filter expression");
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter", e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		float[] embedding = this.embeddingModel.embed(request.getQuery());

		GetBuilder.GetBuilderBuilder builder = GetBuilder.builder();

		GetBuilderBuilder queryBuilder = builder.className(this.options.getObjectClass())
			.withNearVectorFilter(NearVectorArgument.builder()
				.vector(EmbeddingUtils.toFloatArray(embedding))
				.certainty((float) request.getSimilarityThreshold())
				.build())
			.limit(request.getTopK())
			.withWhereFilter(WhereArgument.builder().build()) // adds an empty 'where:{}'
			// placeholder.
			.fields(Fields.builder().fields(this.weaviateSimilaritySearchFields).build());

		String graphQLQuery = queryBuilder.build().buildQuery();

		if (request.hasFilterExpression()) {
			Assert.state(request.getFilterExpression() != null, "filter expression must not be null");
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
		Assert.state(additional != null, "additional field should not be null");
		double certainty = (Double) Objects.requireNonNull(additional.get(ADDITIONAL_CERTAINTY_FIELD_NAME),
				"missing additional certainty field");
		String id = (String) Objects.requireNonNull(additional.get(ADDITIONAL_ID_FIELD_NAME),
				"missing additional id field");

		// Metadata
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadata.DISTANCE.value(), 1 - certainty);

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
		String content = (String) item.get(this.options.getContentFieldName());

		// @formatter:off
		return Document.builder()
			.id(id)
			.text(content)
			.metadata(metadata)
			.score(certainty)
			.build(); // @formatter:on
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.WEAVIATE.value(), operationName)
			.dimensions(this.embeddingModel.dimensions())
			.collectionName(this.options.getObjectClass());
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.weaviateClient;
		return Optional.of(client);
	}

	/**
	 * Defines the consistency levels for Weaviate operations.
	 *
	 * @see <a href=
	 * "https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-consistency-strategies">Weaviate
	 * Consistency Strategies</a>
	 */
	public enum ConsistentLevel {

		/**
		 * Write must receive an acknowledgement from at least one replica node. This is
		 * the fastest (most available), but least consistent option.
		 */
		ONE,

		/**
		 * Write must receive an acknowledgement from at least QUORUM replica nodes.
		 * QUORUM is calculated as n / 2 + 1, where n is the number of replicas.
		 */
		QUORUM,

		/**
		 * Write must receive an acknowledgement from all replica nodes. This is the most
		 * consistent, but 'slowest'.
		 */
		ALL

	}

	/**
	 * Represents a metadata field configuration for Weaviate vector store.
	 *
	 * @param name the name of the metadata field
	 * @param type the type of the metadata field
	 */
	public record MetadataField(String name, Type type) {

		/**
		 * Creates a metadata field of type TEXT.
		 * @param name the name of the field
		 * @return a new MetadataField instance of type TEXT
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public static MetadataField text(String name) {
			Assert.hasText(name, "Text field must not be empty");
			return new MetadataField(name, Type.TEXT);
		}

		/**
		 * Creates a metadata field of type NUMBER.
		 * @param name the name of the field
		 * @return a new MetadataField instance of type NUMBER
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public static MetadataField number(String name) {
			Assert.hasText(name, "Number field must not be empty");
			return new MetadataField(name, Type.NUMBER);
		}

		/**
		 * Creates a metadata field of type BOOLEAN.
		 * @param name the name of the field
		 * @return a new MetadataField instance of type BOOLEAN
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public static MetadataField bool(String name) {
			Assert.hasText(name, "Boolean field name must not be empty");
			return new MetadataField(name, Type.BOOLEAN);
		}

		/**
		 * Defines the supported types for metadata fields.
		 */
		public enum Type {

			TEXT, NUMBER, BOOLEAN

		}
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();

		private ConsistentLevel consistencyLevel = ConsistentLevel.ONE;

		private List<MetadataField> filterMetadataFields = List.of();

		private final WeaviateClient weaviateClient;

		/**
		 * Constructs a new WeaviateBuilder instance.
		 * @param weaviateClient The Weaviate client instance used for database
		 * operations. Must not be null.
		 * @param embeddingModel The embedding model used for vector transformations.
		 * @throws IllegalArgumentException if weaviateClient is null
		 */
		private Builder(WeaviateClient weaviateClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(weaviateClient, "WeaviateClient must not be null");
			this.weaviateClient = weaviateClient;
		}

		/**
		 * Configures the Weaviate vector store option.
		 * @param options the vector store options to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if options is null or empty
		 * @since 1.1.0
		 */
		public Builder options(WeaviateVectorStoreOptions options) {
			Assert.notNull(options, "options must not be empty");
			this.options = options;
			return this;
		}

		/**
		 * Configures the consistency level for Weaviate operations.
		 * @param consistencyLevel the consistency level to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if consistencyLevel is null
		 */
		public Builder consistencyLevel(ConsistentLevel consistencyLevel) {
			Assert.notNull(consistencyLevel, "consistencyLevel must not be null");
			this.consistencyLevel = consistencyLevel;
			return this;
		}

		/**
		 * Configures the filterable metadata fields.
		 * @param filterMetadataFields list of metadata fields that can be used in filters
		 * @return this builder instance
		 * @throws IllegalArgumentException if filterMetadataFields is null
		 */
		public Builder filterMetadataFields(List<MetadataField> filterMetadataFields) {
			Assert.notNull(filterMetadataFields, "filterMetadataFields must not be null");
			this.filterMetadataFields = filterMetadataFields;
			return this;
		}

		/**
		 * Builds and returns a new WeaviateVectorStore instance with the configured
		 * settings.
		 * @return a new WeaviateVectorStore instance
		 * @throws IllegalStateException if the builder configuration is invalid
		 */
		@Override
		public WeaviateVectorStore build() {
			return new WeaviateVectorStore(this);
		}

	}

}
