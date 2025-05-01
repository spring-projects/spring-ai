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
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chroma.ChromaImage;
import org.springframework.ai.chroma.vectorstore.ChromaApi.AddEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.Collection;
import org.springframework.ai.chroma.vectorstore.ChromaApi.GetEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest;
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
		var tenant = this.chromaApi.getTenant(defaultTenantName);
		if (tenant == null) {
			this.chromaApi.createTenant(defaultTenantName);
		}

		var database = this.chromaApi.getDatabase(defaultTenantName, defaultDatabaseName);
		if (database == null) {
			this.chromaApi.createDatabase(defaultTenantName, defaultDatabaseName);
		}

		this.chromaApi.listCollections(defaultTenantName, defaultDatabaseName)
			.forEach(c -> this.chromaApi.deleteCollection(defaultTenantName, defaultDatabaseName, c.name()));
	}

	@Test
	public void testClientWithMetadata() {
		Map<String, Object> metadata = Map.of("hnsw:space", "cosine", "hnsw:M", 5);
		var newCollection = this.chromaApi.createCollection(defaultTenantName, defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection", metadata));
		assertThat(newCollection).isNotNull();
		assertThat(newCollection.name()).isEqualTo("TestCollection");
	}

	@Test
	public void testClient() {
		var newCollection = this.chromaApi.createCollection(defaultTenantName, defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection"));
		assertThat(newCollection).isNotNull();
		assertThat(newCollection.name()).isEqualTo("TestCollection");

		var getCollection = this.chromaApi.getCollection(defaultTenantName, defaultDatabaseName, "TestCollection");
		assertThat(getCollection).isNotNull();
		assertThat(getCollection.name()).isEqualTo("TestCollection");
		assertThat(getCollection.id()).isEqualTo(newCollection.id());

		List<Collection> collections = this.chromaApi.listCollections(defaultTenantName, defaultDatabaseName);
		assertThat(collections).hasSize(1);
		assertThat(collections.get(0).id()).isEqualTo(newCollection.id());

		this.chromaApi.deleteCollection(defaultTenantName, defaultDatabaseName, newCollection.name());
		assertThat(this.chromaApi.listCollections(defaultTenantName, defaultDatabaseName)).hasSize(0);
	}

	@Test
	public void testCollection() {
		var newCollection = this.chromaApi.createCollection(defaultTenantName, defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection"));
		assertThat(this.chromaApi.countEmbeddings(defaultTenantName, defaultDatabaseName, newCollection.id()))
			.isEqualTo(0);

		var addEmbeddingRequest = new AddEmbeddingsRequest(List.of("id1", "id2"),
				List.of(new float[] { 1f, 1f, 1f }, new float[] { 2f, 2f, 2f }),
				List.of(Map.of(), Map.of("key1", "value1", "key2", true, "key3", 23.4)),
				List.of("Hello World", "Big World"));

		this.chromaApi.upsertEmbeddings(defaultTenantName, defaultDatabaseName, newCollection.id(),
				addEmbeddingRequest);

		var addEmbeddingRequest2 = new AddEmbeddingsRequest("id3", new float[] { 3f, 3f, 3f },
				Map.of("key1", "value1", "key2", true, "key3", 23.4), "Big World");

		this.chromaApi.upsertEmbeddings(defaultTenantName, defaultDatabaseName, newCollection.id(),
				addEmbeddingRequest2);

		assertThat(this.chromaApi.countEmbeddings(defaultTenantName, defaultDatabaseName, newCollection.id()))
			.isEqualTo(3);

		var queryResult = this.chromaApi.queryCollection(defaultTenantName, defaultDatabaseName, newCollection.id(),
				new QueryRequest(new float[] { 1f, 1f, 1f }, 3, this.chromaApi.where("""
						{
							"key2" : { "$eq": true }
						}
						""")));
		assertThat(queryResult.ids().get(0)).hasSize(2);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id2", "id3");

		// Update existing embedding.
		this.chromaApi.upsertEmbeddings(defaultTenantName, defaultDatabaseName, newCollection.id(),
				new AddEmbeddingsRequest("id3", new float[] { 6f, 6f, 6f },
						Map.of("key1", "value2", "key2", false, "key4", 23.4), "Small World"));

		var result = this.chromaApi.getEmbeddings(defaultTenantName, defaultDatabaseName, newCollection.id(),
				new GetEmbeddingsRequest(List.of("id2")));
		assertThat(result.ids().get(0)).isEqualTo("id2");

		queryResult = this.chromaApi.queryCollection(defaultTenantName, defaultDatabaseName, newCollection.id(),
				new QueryRequest(new float[] { 1f, 1f, 1f }, 3, this.chromaApi.where("""
						{
							"key2" : { "$eq": true }
						}
						""")));
		assertThat(queryResult.ids().get(0)).hasSize(1);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id2");
	}

	@Test
	public void testQueryWhere() {

		var collection = this.chromaApi.createCollection(defaultTenantName, defaultDatabaseName,
				new ChromaApi.CreateCollectionRequest("TestCollection"));

		var add1 = new AddEmbeddingsRequest("id1", new float[] { 1f, 1f, 1f },
				Map.of("country", "BG", "active", true, "price", 23.4, "year", 2020),
				"The World is Big and Salvation Lurks Around the Corner");

		var add2 = new AddEmbeddingsRequest("id2", new float[] { 1f, 1f, 1f }, Map.of("country", "NL"),
				"The World is Big and Salvation Lurks Around the Corner");

		var add3 = new AddEmbeddingsRequest("id3", new float[] { 1f, 1f, 1f },
				Map.of("country", "BG", "active", false, "price", 40.1, "year", 2023),
				"The World is Big and Salvation Lurks Around the Corner");

		this.chromaApi.upsertEmbeddings(defaultTenantName, defaultDatabaseName, collection.id(), add1);
		this.chromaApi.upsertEmbeddings(defaultTenantName, defaultDatabaseName, collection.id(), add2);
		this.chromaApi.upsertEmbeddings(defaultTenantName, defaultDatabaseName, collection.id(), add3);

		assertThat(this.chromaApi.countEmbeddings(defaultTenantName, defaultDatabaseName, collection.id()))
			.isEqualTo(3);

		var queryResult = this.chromaApi.queryCollection(defaultTenantName, defaultDatabaseName, collection.id(),
				new QueryRequest(new float[] { 1f, 1f, 1f }, 3));

		assertThat(queryResult.ids().get(0)).hasSize(3);
		assertThat(queryResult.ids().get(0)).containsExactlyInAnyOrder("id1", "id2", "id3");

		var chromaEmbeddings = this.chromaApi.toEmbeddingResponseList(queryResult);

		assertThat(chromaEmbeddings).hasSize(3);
		assertThat(chromaEmbeddings).hasSize(3);

		queryResult = this.chromaApi.queryCollection(defaultTenantName, defaultDatabaseName, collection.id(),
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

		queryResult = this.chromaApi.queryCollection(defaultTenantName, defaultDatabaseName, collection.id(),
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
		var collection = this.chromaApi.createCollection(defaultTenantName, defaultDatabaseName,
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

		var collection = this.chromaApi.getCollection(defaultTenantName, defaultDatabaseName, "new-collection");
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
					"Collection non-existent doesn't exist and won't be created as the initializeSchema is set to false.");
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
