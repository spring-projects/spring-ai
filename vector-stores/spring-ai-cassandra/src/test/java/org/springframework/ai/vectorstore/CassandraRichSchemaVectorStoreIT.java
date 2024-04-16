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
package org.springframework.ai.vectorstore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.servererrors.SyntaxError;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig.SchemaColumn;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use `mvn failsafe:integration-test -Dit.test=CassandraRichSchemaVectorStoreIT`
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@Testcontainers
class CassandraRichSchemaVectorStoreIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("cassandra");

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
	static CassandraContainer cassandraContainer = new CassandraContainer(DEFAULT_IMAGE_NAME.withTag("5.0"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Test
	void ensureSchemaCreation() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				Assertions.assertNotNull(store);
				store.checkSchemaValid();
				store.similaritySearch(SearchRequest.query("1843").withTopK(1));
			}
		});
	}

	@Test
	void ensureSchemaNoCreation() {
		this.contextRunner.run(context -> {
			executeCqlFile(context, "test_wiki_full_schema.cql");
			var wrapper = createStore(context, List.of(), true, false);
			try {
				Assertions.assertNotNull(wrapper.store());
				wrapper.store().checkSchemaValid();

				wrapper.store().similaritySearch(SearchRequest.query("1843").withTopK(1));

				wrapper.conf().dropKeyspace();
				executeCqlFile(context, "test_wiki_partial_3_schema.cql");

				// IllegalStateException: column all_minilm_l6_v2_embedding does not exist
				IllegalStateException ise = Assertions.assertThrows(IllegalStateException.class, () -> {
					createStore(context, List.of(), true, false);
				});

				Assertions.assertEquals("column all_minilm_l6_v2_embedding does not exist", ise.getMessage());
			}
			finally {
				wrapper.conf().dropKeyspace();
				wrapper.store().close();
			}
		});
	}

	@Test
	void ensureSchemaPartialCreation() {
		this.contextRunner.run(context -> {
			for (int i = 0; i < 4; ++i) {
				executeCqlFile(context, format("test_wiki_partial_%d_schema.cql", i));
				var wrapper = createStore(context, List.of(), false, false);
				try {
					Assertions.assertNotNull(wrapper.store());
					wrapper.store().checkSchemaValid();

					wrapper.store().similaritySearch(SearchRequest.query("1843").withTopK(1));
					wrapper.conf().dropKeyspace();
				}
				finally {
					wrapper.store().close();
				}
			}
		});
	}

	@Test
	void addAndSearch() {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.query("Neptunes gravity makes its atmosphere").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());

				assertThat(resultDoc.getContent()).contains("Neptunes gravity makes its atmosphere");

				assertThat(resultDoc.getMetadata()).hasSize(3);

				assertThat(resultDoc.getMetadata()).containsKeys("id", "revision",
						CassandraVectorStore.SIMILARITY_FIELD_NAME);

				// Remove all documents from the createStore
				store.delete(documents.stream().map(doc -> doc.getId()).toList());

				results = store.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).isEmpty();
			}
		});
	}

	@Test
	void searchWithPartitionFilter() throws InterruptedException {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				store.add(documents);

				List<Document> results = store.similaritySearch(SearchRequest.query("Great Dark Spot").withTopK(5));
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("wiki == 'simplewiki' && language == 'en' && title == 'Neptune'"));

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

				results = store.similaritySearch(SearchRequest.query("Great Dark Spot")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression(
							"wiki == 'simplewiki' && language == 'en' && title == 'Neptune' && id == 558"));

				assertThat(results).hasSize(3);

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class, () -> {
					store.similaritySearch(SearchRequest.query("Great Dark Spot")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression(
								"NOT(wiki == 'simplewiki' && language == 'en' && title == 'Neptune' && id == 1)"));
				});
			}
		});
	}

	@Test
	void unsearchableFilters() throws InterruptedException {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				store.add(documents);

				List<Document> results = store.similaritySearch(SearchRequest.query("Great Dark Spot").withTopK(5));
				assertThat(results).hasSize(3);

				Assertions.assertThrows(InvalidQueryException.class, () -> {
					store.similaritySearch(SearchRequest.query("The World")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("revision == 9385813"));
				});
			}
		});
	}

	@Test
	void searchWithFilters() throws InterruptedException {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				store.add(documents);

				List<Document> results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY).withTopK(5));
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("id == 558"));

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("id > 557"));

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("id >= 558"));

				assertThat(results).hasSize(3);
				assertThat(results.get(0).getId()).isEqualTo(documents.get(1).getId());

				// cassandra java-driver will throw an error,
				// as chunk_no is not searchable (i.e. no SAI index on it)
				// note, it is possible to have SAI indexes on primary key columns to
				// achieve
				// e.g. searchWithFilterOnPrimaryKeys()
				Assertions.assertThrows(InvalidQueryException.class, () -> {
					store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("id > 557 && \"chunk_no\" == 1"));
				});

				// cassandra server will throw an error,
				// as revision is not searchable (i.e. no SAI index on it)
				Assertions.assertThrows(SyntaxError.class, () -> {
					store.similaritySearch(SearchRequest.query("Great Dark Spot")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("id == 558 || revision == 2020"));
				});

				// cassandra java-driver will throw an error
				Assertions.assertThrows(InvalidQueryException.class, () -> {
					store.similaritySearch(SearchRequest.query("Great Dark Spot")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("NOT(id == 557 || revision == 2020)"));
				});
			}
		});
	}

	@Test
	void searchWithFilterOnPrimaryKeys() throws InterruptedException {
		contextRunner.run(context -> {

			List<SchemaColumn> overrides = List.of(
					new SchemaColumn("title", DataTypes.TEXT, CassandraVectorStoreConfig.SchemaColumnTags.INDEXED),
					new SchemaColumn("chunk_no", DataTypes.INT, CassandraVectorStoreConfig.SchemaColumnTags.INDEXED));

			try (CassandraVectorStore store = createStore(context, overrides, false, true).store()) {

				store.add(documents);

				List<Document> results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY).withTopK(5));
				assertThat(results).hasSize(3);

				store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY)
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("id > 557 && \"chunk_no\" == 1"));

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
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				store.add(documents);

				List<Document> results = store.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY).withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getContent()).contains(URANUS_ORBIT_QUERY);
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

				results = store.similaritySearch(SearchRequest.query(newContent).withTopK(1));

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isNotEqualTo(sameIdDocument.getId());
				assertThat(resultDoc.getContent()).doesNotContain(newContent);

				assertThat(resultDoc.getMetadata()).containsKeys("id", "revision",
						CassandraVectorStore.SIMILARITY_FIELD_NAME);
			}
		});
	}

	@Test
	void searchWithThreshold() {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createStore(context, false).store()) {
				store.add(documents);

				List<Document> fullResult = store
					.similaritySearch(SearchRequest.query(URANUS_ORBIT_QUERY).withTopK(5).withSimilarityThresholdAll());

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get(CassandraVectorStore.SIMILARITY_FIELD_NAME))
					.toList();

				assertThat(distances).hasSize(3);

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = store.similaritySearch(
						SearchRequest.query(URANUS_ORBIT_QUERY).withTopK(5).withSimilarityThreshold(threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(1).getId());

				assertThat(resultDoc.getContent()).contains(URANUS_ORBIT_QUERY);

				assertThat(resultDoc.getMetadata()).containsKeys("id", "revision",
						CassandraVectorStore.SIMILARITY_FIELD_NAME);
			}
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public EmbeddingClient embeddingClient() {
			// default is ONNX all-MiniLM-L6-v2
			return new TransformersEmbeddingClient();
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

	private StoreWrapper<CassandraVectorStore, CassandraVectorStoreConfig> createStore(ApplicationContext context,
			boolean disallowSchemaCreation) throws IOException {

		return createStore(context, List.of(), disallowSchemaCreation, true);
	}

	private StoreWrapper<CassandraVectorStore, CassandraVectorStoreConfig> createStore(ApplicationContext context,
			List<SchemaColumn> extraMetadataFields, boolean disallowSchemaCreation, boolean dropKeyspaceFirst)
			throws IOException {

		Optional<SchemaColumn> wikiOverride = extraMetadataFields.stream()
			.filter((f) -> "wiki".equals(f.name()))
			.findFirst();

		Optional<SchemaColumn> langOverride = extraMetadataFields.stream()
			.filter((f) -> "language".equals(f.name()))
			.findFirst();

		Optional<SchemaColumn> titleOverride = extraMetadataFields.stream()
			.filter((f) -> "title".equals(f.name()))
			.findFirst();

		Optional<SchemaColumn> chunkNoOverride = extraMetadataFields.stream()
			.filter((f) -> "chunk_no".equals(f.name()))
			.findFirst();

		SchemaColumn wikiSC = wikiOverride.orElse(new SchemaColumn("wiki", DataTypes.TEXT));
		SchemaColumn langSC = langOverride.orElse(new SchemaColumn("language", DataTypes.TEXT));
		SchemaColumn titleSC = titleOverride.orElse(new SchemaColumn("title", DataTypes.TEXT));
		SchemaColumn chunkNoSC = chunkNoOverride.orElse(new SchemaColumn("chunk_no", DataTypes.INT));

		List<SchemaColumn> partitionKeys = List.of(wikiSC, langSC, titleSC);
		List<SchemaColumn> clusteringKeys = List.of(chunkNoSC);

		CassandraVectorStoreConfig.Builder builder = CassandraVectorStoreConfig.builder()
			.withCqlSession(context.getBean(CqlSession.class))
			.withKeyspaceName("test_wikidata")
			.withTableName("articles")
			.withPartitionKeys(partitionKeys)
			.withClusteringKeys(clusteringKeys)
			.withContentColumnName("body")
			.withEmbeddingColumnName("all_minilm_l6_v2_embedding")
			.withIndexName("all_minilm_l6_v2_ann")

			.addMetadataColumn(new SchemaColumn("revision", DataTypes.INT),
					new SchemaColumn("id", DataTypes.INT, CassandraVectorStoreConfig.SchemaColumnTags.INDEXED))

			// this store uses '§¶' as a deliminator in the document id between db columns
			// 'title' and 'chunk_no'
			.withPrimaryKeyTranslator((List<Object> primaryKeys) -> {
				if (primaryKeys.isEmpty()) {
					return "test§¶0";
				}
				return format("%s§¶%s", primaryKeys.get(2), primaryKeys.get(3));
			})
			.withDocumentIdTranslator((id) -> {
				String[] parts = id.split("§¶");
				String title = parts[0];
				int chunk_no = 0 < parts.length ? Integer.parseInt(parts[1]) : 0;
				return List.of("simplewiki", "en", title, chunk_no);
			});

		for (SchemaColumn cf : extraMetadataFields) {
			if (!partitionKeys.contains(cf) && !clusteringKeys.contains(cf)) {
				builder = builder.addMetadataColumn(cf);
			}
		}

		if (disallowSchemaCreation) {
			builder = builder.disallowSchemaChanges();
		}

		CassandraVectorStoreConfig conf = builder.build();
		if (dropKeyspaceFirst) {
			conf.dropKeyspace();
		}
		return new StoreWrapper(new CassandraVectorStore(conf, context.getBean(EmbeddingClient.class)), conf);
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

	public record StoreWrapper<K, V>(K store, V conf) {
	}

}
