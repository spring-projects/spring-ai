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

package org.springframework.ai.chroma.vectorstore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chroma.ChromaImage;
import org.springframework.ai.chroma.vectorstore.ChromaApi.AddEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.Collection;
import org.springframework.ai.chroma.vectorstore.ChromaApi.GetEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest;
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Jonghoon Park
 */
@SpringBootTest
@Testcontainers
public class ChromaApiIT {

	@Container
	static ChromaDBContainer chromaContainer = new ChromaDBContainer(ChromaImage.DEFAULT_IMAGE);

	final String defaultTenantName = ChromaApiConstants.DEFAULT_TENANT_NAME;

	final String defaultDatabaseName = ChromaApiConstants.DEFAULT_DATABASE_NAME;

	@Autowired
	ChromaApi chromaApi;

	@Autowired
	EmbeddingModel embeddingModel;

	@BeforeEach
	public void beforeEach() {
		var tenant = this.chromaApi.getTenant(this.defaultTenantName);
		if (tenant == null) {
			this.chromaApi.createTenant(this.defaultTenantName);
		}

		var database = this.chromaApi.getDatabase(this.defaultTenantName, this.defaultDatabaseName);
		if (database == null) {
			this.chromaApi.createDatabase(this.defaultTenantName, this.defaultDatabaseName);
		}

		this.chromaApi.listCollections(this.defaultTenantName, this.defaultDatabaseName)
			.forEach(c -> this.chromaApi.deleteCollection(this.defaultTenantName, this.defaultDatabaseName, c.name()));
	}

	@Test
	public void testClientWithMetadata() {
		Map<String, Object> metadata = Map.of("hnsw:space", "cosine", "hnsw:M", 5);
		var newCollection = this.chromaApi.createCollection(this.defaultTenantName, this.defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection", metadata));
		assertThat(newCollection).isNotNull();
		assertThat(newCollection.name()).isEqualTo("TestCollection");
	}

	@Test
	public void testClient() {
		var newCollection = this.chromaApi.createCollection(this.defaultTenantName, this.defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection"));
		assertThat(newCollection).isNotNull();
		assertThat(newCollection.name()).isEqualTo("TestCollection");

		var getCollection = this.chromaApi.getCollection(this.defaultTenantName, this.defaultDatabaseName,
				"TestCollection");
		assertThat(getCollection).isNotNull();
		assertThat(getCollection.name()).isEqualTo("TestCollection");
		assertThat(getCollection.id()).isEqualTo(newCollection.id());

		List<Collection> collections = this.chromaApi.listCollections(this.defaultTenantName, this.defaultDatabaseName);
		assertThat(collections).hasSize(1);
		assertThat(collections.get(0).id()).isEqualTo(newCollection.id());

		this.chromaApi.deleteCollection(this.defaultTenantName, this.defaultDatabaseName, newCollection.name());
		assertThat(this.chromaApi.listCollections(this.defaultTenantName, this.defaultDatabaseName)).hasSize(0);
	}

	@Test
	public void testCollection() {
		var newCollection = this.chromaApi.createCollection(this.defaultTenantName, this.defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection"));
		assertThat(this.chromaApi.countEmbeddings(this.defaultTenantName, this.defaultDatabaseName, newCollection.id()))
			.isEqualTo(0);

		var addEmbeddingRequest = new AddEmbeddingsRequest(List.of("id1", "id2"),
				List.of(new float[] { 1f, 1f, 1f }, new float[] { 2f, 2f, 2f }),
				List.of(Map.of(), Map.of("key1", "value1", "key2", true, "key3", 23.4)),
				List.of("Hello World", "Big World"));

		this.chromaApi.upsertEmbeddings(this.defaultTenantName, this.defaultDatabaseName, newCollection.id(),
				addEmbeddingRequest);

		var addEmbeddingRequest2 = new AddEmbeddingsRequest("id3", new float[] { 3f, 3f, 3f },
				Map.of("key1", "value1", "key2", true, "key3", 23.4), "Big World");

		this.chromaApi.upsertEmbeddings(this.defaultTenantName, this.defaultDatabaseName, newCollection.id(),
				addEmbeddingRequest2);

		assertThat(this.chromaApi.countEmbeddings(this.defaultTenantName, this.defaultDatabaseName, newCollection.id()))
			.isEqualTo(3);

		var queryResult = this.chromaApi.queryCollection(this.defaultTenantName, this.defaultDatabaseName,
				newCollection.id(), new QueryRequest(new float[] { 1f, 1f, 1f }, 3, this.chromaApi.where("""
						{
							"key2" : { "$eq": true }
						}
						""")));
		assertThat(queryResult.ids().get(0)).hasSize(2);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id2", "id3");

		// Update existing embedding.
		this.chromaApi.upsertEmbeddings(this.defaultTenantName, this.defaultDatabaseName, newCollection.id(),
				new AddEmbeddingsRequest("id3", new float[] { 6f, 6f, 6f },
						Map.of("key1", "value2", "key2", false, "key4", 23.4), "Small World"));

		var result = this.chromaApi.getEmbeddings(this.defaultTenantName, this.defaultDatabaseName, newCollection.id(),
				new GetEmbeddingsRequest(List.of("id2")));
		assertThat(result.ids().get(0)).isEqualTo("id2");

		queryResult = this.chromaApi.queryCollection(this.defaultTenantName, this.defaultDatabaseName,
				newCollection.id(), new QueryRequest(new float[] { 1f, 1f, 1f }, 3, this.chromaApi.where("""
						{
							"key2" : { "$eq": true }
						}
						""")));
		assertThat(queryResult.ids().get(0)).hasSize(1);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id2");
	}

	@Test
	public void testQueryWhere() {

		var collection = this.chromaApi.createCollection(this.defaultTenantName, this.defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection"));

		var add1 = new AddEmbeddingsRequest("id1", new float[] { 1f, 1f, 1f },
				Map.of("country", "BG", "active", true, "price", 23.4, "year", 2020),
				"The World is Big and Salvation Lurks Around the Corner");

		var add2 = new AddEmbeddingsRequest("id2", new float[] { 1f, 1f, 1f }, Map.of("country", "NL"),
				"The World is Big and Salvation Lurks Around the Corner");

		var add3 = new AddEmbeddingsRequest("id3", new float[] { 1f, 1f, 1f },
				Map.of("country", "BG", "active", false, "price", 40.1, "year", 2023),
				"The World is Big and Salvation Lurks Around the Corner");

		this.chromaApi.upsertEmbeddings(this.defaultTenantName, this.defaultDatabaseName, collection.id(), add1);
		this.chromaApi.upsertEmbeddings(this.defaultTenantName, this.defaultDatabaseName, collection.id(), add2);
		this.chromaApi.upsertEmbeddings(this.defaultTenantName, this.defaultDatabaseName, collection.id(), add3);

		assertThat(this.chromaApi.countEmbeddings(this.defaultTenantName, this.defaultDatabaseName, collection.id()))
			.isEqualTo(3);

		var queryResult = this.chromaApi.queryCollection(this.defaultTenantName, this.defaultDatabaseName,
				collection.id(), new QueryRequest(new float[] { 1f, 1f, 1f }, 3));

		assertThat(queryResult.ids().get(0)).hasSize(3);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id1", "id2", "id3");

		var chromaEmbeddings = this.chromaApi.toEmbeddingResponseList(queryResult);

		assertThat(chromaEmbeddings).hasSize(3);
		assertThat(chromaEmbeddings).hasSize(3);

		queryResult = this.chromaApi.queryCollection(this.defaultTenantName, this.defaultDatabaseName, collection.id(),
				new QueryRequest(new float[] { 1f, 1f, 1f }, 3, this.chromaApi.where("""
						{
							"$and" : [
								{"country" : { "$eq": "BG"}},
								{"year" : { "$gte": 2020}}
							]
						}
						""")));
		assertThat(queryResult.ids().get(0)).hasSize(2);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id1", "id3");

		queryResult = this.chromaApi.queryCollection(this.defaultTenantName, this.defaultDatabaseName, collection.id(),
				new QueryRequest(new float[] { 1f, 1f, 1f }, 3, this.chromaApi.where("""
						{
							"$and" : [
								{"country" : { "$eq": "BG"}},
								{"year" : { "$gte": 2020}},
								{"active" : { "$eq": true}}
							]
						}
						""")));
		assertThat(queryResult.ids().get(0)).hasSize(1);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id1");
	}

	@Test
	void shouldUseExistingCollectionWhenSchemaInitializationDisabled() { // initializeSchema
																			// is false by
																			// default.
		var collection = this.chromaApi.createCollection(this.defaultTenantName, this.defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("test-collection"));
		assertThat(collection).isNotNull();
		assertThat(collection.name()).isEqualTo("test-collection");

		ChromaVectorStore store = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("test-collection")
			.initializeImmediately(true)
			.build();

		Document document = new Document("test content");
		assertThatNoException().isThrownBy(() -> store.add(Collections.singletonList(document)));
	}

	@Test
	void shouldCreateNewCollectionWhenSchemaInitializationEnabled() {
		ChromaVectorStore store = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("new-collection")
			.initializeSchema(true)
			.initializeImmediately(true)
			.build();

		var collection = this.chromaApi.getCollection(this.defaultTenantName, this.defaultDatabaseName,
				"new-collection");
		assertThat(collection).isNotNull();
		assertThat(collection.name()).isEqualTo("new-collection");

		Document document = new Document("test content");
		assertThatNoException().isThrownBy(() -> store.add(Collections.singletonList(document)));
	}

	@Test
	void shouldFailWhenCollectionDoesNotExist() {
		assertThatThrownBy(() -> ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("non-existent")
			.initializeSchema(false)
			.initializeImmediately(true)
			.build()).isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to initialize ChromaVectorStore")
			.hasCauseInstanceOf(RuntimeException.class)
			.hasRootCauseMessage(
					"Collection non-existent with the tenant: SpringAiTenant and the database: SpringAiDatabase doesn't exist and won't be created as the initializeSchema is set to false.");
	}

	@Test
	public void testAddEmbeddingsRequestMetadataConversion() {
		Map<String, Object> metadata = Map.of("intVal", 42, "boolVal", true, "strVal", "hello", "doubleVal", 3.14,
				"listVal", List.of(1, 2, 3), "mapVal", Map.of("a", 1, "b", 2));
		AddEmbeddingsRequest req = new AddEmbeddingsRequest("id", new float[] { 1f, 2f, 3f }, metadata, "doc");
		Map<String, Object> processed = req.metadata().get(0);

		assertThat(processed.get("intVal")).isInstanceOf(Integer.class);
		assertThat(processed.get("boolVal")).isInstanceOf(Boolean.class);
		assertThat(processed.get("strVal")).isInstanceOf(String.class);
		assertThat(processed.get("doubleVal")).isInstanceOf(Number.class).isEqualTo(3.14);
		assertThat(processed.get("listVal")).isInstanceOf(String.class).isEqualTo("[1,2,3]");
		assertThat(processed.get("mapVal")).isInstanceOf(String.class);
		net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson(processed.get("mapVal")).isEqualTo("{a:1,b:2}");
	}

	@Test
	void shouldCreateCollectionWithCosineDistanceType() {
		ChromaVectorStore store = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("cosine-collection")
			.initializeSchema(true)
			.distanceType(ChromaVectorStore.ChromaDistanceType.COSINE)
			.initializeImmediately(true)
			.build();

		var collection = this.chromaApi.getCollection(this.defaultTenantName, this.defaultDatabaseName,
				"cosine-collection");
		assertThat(collection).isNotNull();
		assertThat(collection.name()).isEqualTo("cosine-collection");
		assertThat(collection.metadata()).containsEntry("hnsw:space", "cosine");
	}

	@Test
	void shouldCreateCollectionWithEuclideanDistanceType() {
		ChromaVectorStore store = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("euclidean-collection")
			.initializeSchema(true)
			.distanceType(ChromaVectorStore.ChromaDistanceType.EUCLIDEAN)
			.initializeImmediately(true)
			.build();

		var collection = this.chromaApi.getCollection(this.defaultTenantName, this.defaultDatabaseName,
				"euclidean-collection");
		assertThat(collection).isNotNull();
		assertThat(collection.metadata()).containsEntry("hnsw:space", "l2");
	}

	@Test
	void shouldCreateCollectionWithInnerProductDistanceType() {
		ChromaVectorStore store = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("ip-collection")
			.initializeSchema(true)
			.distanceType(ChromaVectorStore.ChromaDistanceType.INNER_PRODUCT)
			.initializeImmediately(true)
			.build();

		var collection = this.chromaApi.getCollection(this.defaultTenantName, this.defaultDatabaseName,
				"ip-collection");
		assertThat(collection).isNotNull();
		assertThat(collection.metadata()).containsEntry("hnsw:space", "ip");
	}

	@Test
	void shouldCreateCollectionWithCustomHnswParameters() {
		ChromaVectorStore store = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("custom-hnsw-collection")
			.initializeSchema(true)
			.efConstruction(200)
			.efSearch(50)
			.distanceType(ChromaVectorStore.ChromaDistanceType.COSINE)
			.initializeImmediately(true)
			.build();

		var collection = this.chromaApi.getCollection(this.defaultTenantName, this.defaultDatabaseName,
				"custom-hnsw-collection");
		assertThat(collection).isNotNull();
		assertThat(collection.metadata()).containsEntry("hnsw:space", "cosine");
		assertThat(collection.metadata()).containsEntry("hnsw:construction_ef", 200);
		assertThat(collection.metadata()).containsEntry("hnsw:search_ef", 50);
	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public ChromaApi chromaApi() {
			return ChromaApi.builder().baseUrl(chromaContainer.getEndpoint()).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
