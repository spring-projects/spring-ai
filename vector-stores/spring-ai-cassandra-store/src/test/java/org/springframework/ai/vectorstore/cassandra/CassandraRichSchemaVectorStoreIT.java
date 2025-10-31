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

package org.springframework.ai.vectorstore.cassandra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.servererrors.SyntaxError;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use `mvn failsafe:integration-test -Dit.test=CassandraRichSchemaVectorStoreIT`
 *
 * @author Mick Semb Wever
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Testcontainers
class CassandraRichSchemaVectorStoreIT {

	private static final Logger logger = LoggerFactory.getLogger(CassandraRichSchemaVectorStoreIT.class);

	private static final List<Document> documents = List.of(

			new Document("Neptune§¶0",
					"Neptune\\n\\nThis article contains special characters. Without proper rendering support, you may see question marks, boxes, or other symbols. Neptune is the eighth and farthest planet from the Sun in the Solar System. It is an ice giant. It is the fourth-largest planet in the system. Neptunes mass is 17 times Earths mass and a little bit more than Uranus mass. Neptune is denser and smaller than Uranus. Because of its greater mass, Neptunes gravity makes its atmosphere smaller and denser. It was named after the Roman god of the sea, Neptune. Neptunes astronomical symbol is ♆, the trident of the god Neptune. Neptunes atmosphere is mostly hydrogen and helium. It also contains small amounts of methane which makes the planet appear blue. Neptunes blue color is similar, but slightly darker, than the color of Uranus. Neptune also has the strongest winds of any planet in the Solar System, as high as 2,100\\xa0km/h or 1,300\\xa0mph. Urbain Le Verrier and John Couch Adams were the astronomers who discovered Neptune. Neptune was not",
					Map.of("revision", 9385813, "id", 558)),

			new Document("Neptune§¶1",
					"Neptune\\n\\nbut slightly darker, than the color of Uranus. Neptune also has the strongest winds of any planet in the Solar System, as high as 2,100\\xa0km/h or 1,300\\xa0mph. Urbain Le Verrier and John Couch Adams were the astronomers who discovered Neptune. Neptune was not discovered using a telescope. It was the first planet to be discovered using mathematics. In 1821, astronomers saw that Uranus orbit was different from what they expected. Another nearby planets mass was changing Uranus orbit. They found Neptune was the cause. Voyager 2 visited Neptune on 25 August 1989. It was the only spacecraft to visit the planet. Neptune used to have a huge storm known as the \"Great Dark Spot\". Voyager 2 discovered the spot in 1989. The dark spot was not seen in 1994, but new spots were found since then. It is not known why the dark spot disappeared. Visits by other space probes have been planned. Neptune has five rings surrounding it, however, it is hard too see from Earth due to the distance from Neptune. Galileo Galilei was the first",
					Map.of("revision", 9385813, "id", 558)),

			new Document("Neptune§¶2",
					"Neptune\\n\\nfound since then. It is not known why the dark spot disappeared. Visits by other space probes have been planned. Neptune has five rings surrounding it, however, it is hard too see from Earth due to the distance from Neptune. Galileo Galilei was the first person who saw Neptune. He saw it on 28 December 1612 and 27 January 1613. His drawings showed points near Jupiter where Neptune is placed. But Galileo was not credited for the discovery. He thought Neptune was a \"fixed star\" instead of a planet. Because Neptune slowly moved across the sky, Galileos small telescope was not strong enough to see that Neptune was a planet. In 1821, Alexis Bouvard published the astronomical tables of the orbit of Uranus. Later observations showed that Uranus was moving in an irregular way in its orbit. Some astronomers thought this was caused by another large body. In 1843, John Couch Adams calculated the orbit of an eighth planet that could possibly affect the orbit of Uranus. He sent his calculations to Sir George Airy, the",
					Map.of("revision", 9385813, "id", 558)));

	private static final String URANUS_ORBIT_QUERY = "It was the first planet to be discovered using mathematics. In 1821, astronomers saw that Uranus orbit was different from what they expected. Another nearby planets mass was changing Uranus orbit.";

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(CassandraImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	static CassandraVectorStore.Builder storeBuilder(ApplicationContext context,
			List<CassandraVectorStore.SchemaColumn> columnOverrides) throws IOException {

		Optional<CassandraVectorStore.SchemaColumn> wikiOverride = columnOverrides.stream()
			.filter(f -> "wiki".equals(f.name()))
			.findFirst();

		Optional<CassandraVectorStore.SchemaColumn> langOverride = columnOverrides.stream()
			.filter(f -> "language".equals(f.name()))
			.findFirst();

		Optional<CassandraVectorStore.SchemaColumn> titleOverride = columnOverrides.stream()
			.filter(f -> "title".equals(f.name()))
			.findFirst();

		Optional<CassandraVectorStore.SchemaColumn> chunkNoOverride = columnOverrides.stream()
			.filter(f -> "chunk_no".equals(f.name()))
			.findFirst();

		var wikiSC = wikiOverride.orElse(new CassandraVectorStore.SchemaColumn("wiki", DataTypes.TEXT));
		var langSC = langOverride.orElse(new CassandraVectorStore.SchemaColumn("language", DataTypes.TEXT));
		var titleSC = titleOverride.orElse(new CassandraVectorStore.SchemaColumn("title", DataTypes.TEXT));
		var chunkNoSC = chunkNoOverride.orElse(new CassandraVectorStore.SchemaColumn("chunk_no", DataTypes.INT));

		List<CassandraVectorStore.SchemaColumn> partitionKeys = List.of(wikiSC, langSC, titleSC);
		List<CassandraVectorStore.SchemaColumn> clusteringKeys = List.of(chunkNoSC);

		return CassandraVectorStore.builder(context.getBean(EmbeddingModel.class))
			.session(context.getBean(CqlSession.class))
			.keyspace("test_wikidata")
			.table("articles")
			.partitionKeys(partitionKeys)
			.clusteringKeys(clusteringKeys)
			.contentColumnName("body")
			.embeddingColumnName("all_minilm_l6_v2_embedding")
			.indexName("all_minilm_l6_v2_ann")
			.addMetadataColumns(new CassandraVectorStore.SchemaColumn("revision", DataTypes.INT),
					new CassandraVectorStore.SchemaColumn("id", DataTypes.INT,
							CassandraVectorStore.SchemaColumnTags.INDEXED))
			// this store uses '§¶' as a deliminator in the document id between db columns
			// 'title' and 'chunk_no'
			.primaryKeyTranslator((List<Object> primaryKeys) -> {
				if (primaryKeys.isEmpty()) {
					return "test§¶0";
				}
				return String.format("%s§¶%s", primaryKeys.get(2), primaryKeys.get(3));
			})
			.documentIdTranslator(id -> {
				String[] parts = id.split("§¶");
				String title = parts[0];
				int chunk_no = 0 < parts.length ? Integer.parseInt(parts[1]) : 0;
				return List.of("simplewiki", "en", title, chunk_no);
			});
	}

	@Test
	void ensureSchemaCreation() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				Assertions.assertNotNull(store);
				store.checkSchemaValid();
				store.similaritySearch(SearchRequest.builder().query("1843").topK(1).build());
			}
		});
	}

	@Test
	void ensureSchemaNoCreation() {
		this.contextRunner.run(context -> {
			executeCqlFile(context, "test_wiki_full_schema.cql");
			var builder = createBuilder(context, List.of(), false, false);
			Assertions.assertNotNull(builder);
			var store = new CassandraVectorStore(builder);
			try {

				store.checkSchemaValid();

				store.similaritySearch(SearchRequest.builder().query("1843").topK(1).build());

				CassandraVectorStore.dropKeyspace(builder);
				executeCqlFile(context, "test_wiki_partial_3_schema.cql");

				// IllegalStateException: column all_minilm_l6_v2_embedding does not exist
				IllegalStateException ise = Assertions.assertThrows(IllegalStateException.class,
						() -> createStore(context, List.of(), false, false));

				Assertions.assertEquals("index all_minilm_l6_v2_ann does not exist", ise.getMessage());
			}
			finally {
				CassandraVectorStore.dropKeyspace(builder);
				store.close();
			}
		});
	}

	@Test
	void ensureSchemaPartialCreation() {
		this.contextRunner.run(context -> {
			int PARTIAL_FILES = 5;
			for (int i = 0; i < PARTIAL_FILES; ++i) {
				executeCqlFile(context, java.lang.String.format("test_wiki_partial_%d_schema.cql", i));
				var builder = createBuilder(context, List.of(), true, false);
				Assertions.assertNotNull(builder);
				CassandraVectorStore.dropKeyspace(builder);
				var store = builder.build();
				try {
					store.checkSchemaValid();

					store.similaritySearch(SearchRequest.builder().query("1843").topK(1).build());
				}
				finally {
					CassandraVectorStore.dropKeyspace(builder);
					store.close();
				}
			}
			// make sure there's not more files to test
			Assertions.assertThrows(IOException.class, () -> executeCqlFile(context,
					java.lang.String.format("test_wiki_partial_%d_schema.cql", PARTIAL_FILES)));
		});
	}

	@Test
	void addAndSearch() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				store.add(documents);

				List<Document> results = store.similaritySearch(
						SearchRequest.builder().query("Neptunes gravity makes its atmosphere").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());

				assertThat(resultDoc.getText()).contains("Neptunes gravity makes its atmosphere");

				assertThat(resultDoc.getMetadata()).hasSize(3);

				assertThat(resultDoc.getMetadata()).containsKeys("id", "revision", DocumentMetadata.DISTANCE.value());

				// Remove all documents from the createStore
				store.delete(documents.stream().map(doc -> doc.getId()).toList());

				results = store.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
				assertThat(results).isEmpty();
			}
		});
	}

	@Test
	void addAndSearchPoormansBench() {
		// todo – replace with JMH (parameters: nThreads, rounds, runs, docsPerAdd)
		int nThreads = CassandraVectorStore.DEFAULT_ADD_CONCURRENCY;
		int runs = 10; // 100;
		int docsPerAdd = 12; // 128;
		int rounds = 3;

		this.contextRunner.run(context -> {

			try (CassandraVectorStore store = storeBuilder(context, List.of()).fixedThreadPoolExecutorSize(nThreads)
				.build()) {

				var executor = Executors.newFixedThreadPool((int) (nThreads * 1.2));
				for (int k = 0; k < rounds; ++k) {
					long start = System.nanoTime();
					var futures = new CompletableFuture[runs];
					for (int j = 0; j < runs; ++j) {
						futures[j] = CompletableFuture.runAsync(() -> {
							List<Document> documents = new ArrayList<>();
							for (int i = docsPerAdd; i >= 0; --i) {

								documents.add(new Document(
										RandomStringUtils.randomAlphanumeric(4) + "§¶"
												+ ThreadLocalRandom.current().nextInt(1, 10),
										RandomStringUtils.randomAlphanumeric(1024), Map.of("revision",
												ThreadLocalRandom.current().nextInt(1, 100000), "id", 1000)));
							}
							store.add(documents);

							var results = store.similaritySearch(SearchRequest.builder()
								.query(RandomStringUtils.randomAlphanumeric(20))
								.topK(10)
								.build());

							assertThat(results).hasSize(10);
						}, executor);
					}
					CompletableFuture.allOf(futures).join();
					long time = System.nanoTime() - start;
					logger.info("add+search took an average of {} ms", Duration.ofNanos(time / runs).toMillis());
				}
			}
		});
	}

	@Test
	void searchWithPartitionFilter() throws InterruptedException {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("Great Dark Spot").topK(5).build());
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.builder()
					.query(URANUS_ORBIT_QUERY)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("wiki == 'simplewiki' && language == 'en' && title == 'Neptune'")
					.build());

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				// BUG CASSANDRA-19544
				// should be able to restrict on clustering keys (when filtering isn't
				// required)
				//
				// results = store.similaritySearch(SearchRequest.query("Great Dark Spot")
				// .withTopK(5)
				// .withSimilarityThresholdAll()
				// .withFilterExpression(
				// "wiki == 'simplewiki' && language == 'en' && title == 'Neptune' &&
				// \"chunk_no\" == 0"));
				//
				// assertThat(results).hasSize(1);
				// assertThat(results.get(0).getId()).isEqualTo(documents.get(0).getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query("Great Dark Spot")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("wiki == 'simplewiki' && language == 'en' && title == 'Neptune' && id == 558")
					.build());

				assertThat(results).hasSize(3);

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("Great Dark Spot")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression(
									"NOT(wiki == 'simplewiki' && language == 'en' && title == 'Neptune' && id == 1)")
							.build()));
			}
		});
	}

	@Test
	void unsearchableFilters() throws InterruptedException {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("Great Dark Spot").topK(5).build());
				assertThat(results).hasSize(3);

				Assertions.assertThrows(InvalidQueryException.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("The World")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("revision == 9385813")
							.build()));
			}
		});
	}

	@Test
	void searchWithFilters() throws InterruptedException {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query(URANUS_ORBIT_QUERY).topK(5).build());
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.builder()
					.query(URANUS_ORBIT_QUERY)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("id == 558")
					.build());

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query(URANUS_ORBIT_QUERY)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("id > 557")
					.build());

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query(URANUS_ORBIT_QUERY)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("id >= 558")
					.build());

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				// cassandra java-driver will throw an error,
				// as chunk_no is not searchable (i.e. no SAI index on it)
				// note, it is possible to have SAI indexes on primary key columns to
				// achieve
				// e.g. searchWithFilterOnPrimaryKeys()
				Assertions.assertThrows(InvalidQueryException.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query(URANUS_ORBIT_QUERY)
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("id > 557 && \"chunk_no\" == 1")
							.build()));

				// cassandra server will throw an error,
				// as revision is not searchable (i.e. no SAI index on it)
				Assertions.assertThrows(SyntaxError.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("Great Dark Spot")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("id == 558 || revision == 2020")
							.build()));

				// cassandra java-driver will throw an error
				Assertions.assertThrows(InvalidQueryException.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("Great Dark Spot")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("NOT(id == 557 || revision == 2020)")
							.build()));
			}
		});
	}

	@Test
	void searchWithFilterOnPrimaryKeys() throws InterruptedException {
		this.contextRunner.run(context -> {

			List<SchemaColumn> overrides = List.of(
					new SchemaColumn("title", DataTypes.TEXT, CassandraVectorStore.SchemaColumnTags.INDEXED),
					new SchemaColumn("chunk_no", DataTypes.INT, CassandraVectorStore.SchemaColumnTags.INDEXED));

			try (CassandraVectorStore store = createStore(context, overrides, true, true)) {

				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query(URANUS_ORBIT_QUERY).topK(5).build());
				assertThat(results).hasSize(3);

				store.similaritySearch(SearchRequest.builder()
					.query(URANUS_ORBIT_QUERY)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("id > 557 && \"chunk_no\" == 1")
					.build());

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				// Cassandra java-driver bug, not detecting index on title exists
				//
				// store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
				// .withTopK(5)
				// .withSimilarityThresholdAll()
				// .withFilterExpression("id > 557 && title == 'Neptune'"));
				//
				// assertThat(results).hasSize(3);
				// assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());
			}
		});
	}

	@Test
	void documentUpdate() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query(URANUS_ORBIT_QUERY).topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getText()).contains(URANUS_ORBIT_QUERY);
				assertThat(resultDoc.getMetadata()).containsKey("revision");

				String newContent = "The World is Big and Salvation Lurks Around the Corner";

				Document sameIdDocument = new Document(documents.get(1).getId(), newContent, Collections.emptyMap());

				// BUG in Cassandra 5.0-beta1
				// uncomment when 5.0-beta2 is release and cassandraContainer pulls it
				//
				// store.add(List.of(sameIdDocument));
				//
				// results =
				// store.similaritySearch(SearchRequest.query(newContent).withTopK(1));
				//
				// assertThat(results).hasSize(1);
				// resultDoc = results.get(0);
				// assertThat(resultDoc.getId()).isEqualTo(sameIdDocument.getId());
				// assertThat(resultDoc.getContent()).contains(newContent);
				//
				// // the empty metadata map will not overwrite the row's existing "id"
				// and
				// // "revision" values
				// assertThat(resultDoc.getMetadata()).containsKeys("id", "revision",
				// CassandraVectorStore.SIMILARITY_FIELD_NAME);

				store.delete(List.of(sameIdDocument.getId()));

				results = store.similaritySearch(SearchRequest.builder().query(newContent).topK(1).build());

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isNotEqualTo(sameIdDocument.getId());
				assertThat(resultDoc.getText()).doesNotContain(newContent);

				assertThat(resultDoc.getMetadata()).containsKeys("id", "revision", DocumentMetadata.DISTANCE.value());
			}
		});
	}

	@Test
	void searchWithThreshold() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, true)) {
				store.add(documents);

				List<Document> fullResult = store.similaritySearch(
						SearchRequest.builder().query(URANUS_ORBIT_QUERY).topK(5).similarityThresholdAll().build());

				List<Double> scores = fullResult.stream().map(Document::getScore).toList();

				assertThat(scores).hasSize(3);

				double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

				List<Document> results = store.similaritySearch(SearchRequest.builder()
					.query(URANUS_ORBIT_QUERY)
					.topK(5)
					.similarityThreshold(similarityThreshold)
					.build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(1).getId());

				assertThat(resultDoc.getText()).contains(URANUS_ORBIT_QUERY);

				assertThat(resultDoc.getMetadata()).containsKeys("id", "revision", DocumentMetadata.DISTANCE.value());
				assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
			}
		});
	}

	private CassandraVectorStore createStore(ApplicationContext context, boolean initializeSchema) throws IOException {

		return createStore(context, List.of(), initializeSchema, true);
	}

	private CassandraVectorStore createStore(ApplicationContext context, List<SchemaColumn> columnOverrides,
			boolean initializeSchema, boolean dropKeyspaceFirst) throws IOException {

		CassandraVectorStore.Builder builder = storeBuilder(context, columnOverrides);
		builder.initializeSchema(initializeSchema);

		if (dropKeyspaceFirst) {
			CassandraVectorStore.dropKeyspace(builder);
		}

		return new CassandraVectorStore(builder);
	}

	private CassandraVectorStore.Builder createBuilder(ApplicationContext context, List<SchemaColumn> columnOverrides,
			boolean initailzeSchema, boolean dropKeyspaceFirst) throws IOException {

		CassandraVectorStore.Builder builder = storeBuilder(context, columnOverrides);
		builder.initializeSchema(initailzeSchema);

		if (dropKeyspaceFirst) {
			CassandraVectorStore.dropKeyspace(builder);
		}

		return builder;
	}

	private void executeCqlFile(ApplicationContext context, String filename) throws IOException {
		logger.info("executing {}", filename);

		CqlSession session = context.getBean(CqlSession.class);

		String[] cql = new DefaultResourceLoader().getResource(filename)
			.getContentAsString(StandardCharsets.UTF_8)
			.trim()
			.split(";");

		for (var c : cql) {
			session.execute(c.trim());
		}
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public EmbeddingModel embeddingModel() {
			// default is ONNX all-MiniLM-L6-v2
			return new TransformersEmbeddingModel();
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// comment next two lines out to connect to a local C* cluster
				.addContactPoint(cassandraContainer.getContactPoint())
				.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
				.build();
		}

	}

}
