package org.springframework.ai.vectorstore.pgvector;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PgVectorStoreCustomColumnNamesIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
			.withUsername("postgres")
			.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestApplication.class)
			.withPropertyValues(
					"test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",

					// JdbcTemplate configuration
					String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
							postgresContainer.getMappedPort(5432), "postgres"),
					"app.datasource.username=postgres", "app.datasource.password=postgres",
					"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
	}

	private static boolean isTableExists(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '" + tableName + "')",
				Boolean.class);
	}

	private static boolean isColumnExists(ApplicationContext context, String schemaName, String tableName,
										  String columnName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = """
				SELECT EXISTS (
					SELECT 1
					FROM information_schema.columns
					WHERE table_schema = ? AND table_name = ? AND column_name = ?
				)
				""";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName, columnName);
	}

	@Test
	public void shouldCreateTableWithCustomColumnNames() {

		String tableName = "custom_col_vector_table";

		// Custom column names
		String idCol = "doc_id";
		String contentCol = "doc_text";
		String metadataCol = "doc_metadata";
		String embeddingCol = "doc_embedding";

		this.contextRunner.withPropertyValues(
						"test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
						"test.spring.ai.vectorstore.pgvector.idColumn=" + idCol,
						"test.spring.ai.vectorstore.pgvector.contentColumn=" + contentCol,
						"test.spring.ai.vectorstore.pgvector.metadataColumn=" + metadataCol,
						"test.spring.ai.vectorstore.pgvector.embeddingColumn=" + embeddingCol)
				.run(context -> {

					assertThat(context).hasNotFailed();
					assertThat(isTableExists(context, tableName)).isTrue();

					assertThat(isColumnExists(context, "public", tableName, idCol)).isTrue();
					assertThat(isColumnExists(context, "public", tableName, contentCol)).isTrue();
					assertThat(isColumnExists(context, "public", tableName, metadataCol)).isTrue();
					assertThat(isColumnExists(context, "public", tableName, embeddingCol)).isTrue();

					dropTableByName(context, tableName);
				});
	}

	@Test
	public void shouldInsertDocumentWithCustomColumnNames() {

		String tableName = "custom_col_vector_table_insert";

		// Custom column names
		String idCol = "doc_id";
		String contentCol = "doc_text";
		String metadataCol = "doc_metadata";
		String embeddingCol = "doc_embedding";

		this.contextRunner.withPropertyValues(
						"test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
						"test.spring.ai.vectorstore.pgvector.idColumn=" + idCol,
						"test.spring.ai.vectorstore.pgvector.contentColumn=" + contentCol,
						"test.spring.ai.vectorstore.pgvector.metadataColumn=" + metadataCol,
						"test.spring.ai.vectorstore.pgvector.embeddingColumn=" + embeddingCol)
				.run(context -> {

					assertThat(context).hasNotFailed();

					VectorStore store = context.getBean(VectorStore.class);

					store.add(java.util.List.of(new Document("hello pgvector", java.util.Map.of("k", "v"))));

					JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

					Integer count = jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM " + "public." + tableName, Integer.class);

					assertThat(count).isNotNull();
					assertThat(count).isGreaterThan(0);

					dropTableByName(context, tableName);
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.pgvector.vectorTableName:}")
		String vectorTableName;

		@Value("${test.spring.ai.vectorstore.pgvector.schemaName:public}")
		String schemaName;

		@Value("${test.spring.ai.vectorstore.pgvector.schemaValidation:false}")
		boolean schemaValidation;

		@Value("${test.spring.ai.vectorstore.pgvector.idColumn:id}")
		String idColumn;

		@Value("${test.spring.ai.vectorstore.pgvector.contentColumn:content}")
		String contentColumn;

		@Value("${test.spring.ai.vectorstore.pgvector.metadataColumn:metadata}")
		String metadataColumn;

		@Value("${test.spring.ai.vectorstore.pgvector.embeddingColumn:embedding}")
		String embeddingColumn;

		int dimensions = 1536;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {

			PgVectorColumnNames columns = PgVectorColumnNames.builder()
					.id(this.idColumn)
					.content(this.contentColumn)
					.metadata(this.metadataColumn)
					.embedding(this.embeddingColumn)
					.build();

			return PgVectorStore.builder(jdbcTemplate, embeddingModel)
					.schemaName(this.schemaName)
					.vectorTableName(this.vectorTableName)
					.vectorTableValidationsEnabled(this.schemaValidation)
					.dimensions(this.dimensions)
					.distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
					.removeExistingVectorStoreTable(true)
					.indexType(PgVectorStore.PgIndexType.HNSW)
					.initializeSchema(true)
					.columnMapping(columns)
					.build();
		}

		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		@Primary
		@ConfigurationProperties("app.datasource")
		public DataSourceProperties dataSourceProperties() {
			return new DataSourceProperties();
		}

		@Bean
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}
}
