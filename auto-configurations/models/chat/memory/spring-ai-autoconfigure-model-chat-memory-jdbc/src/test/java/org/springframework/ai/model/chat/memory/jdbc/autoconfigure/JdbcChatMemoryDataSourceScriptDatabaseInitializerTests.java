package org.springframework.ai.model.chat.memory.jdbc.autoconfigure;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
@Testcontainers
class JdbcChatMemoryDataSourceScriptDatabaseInitializerTests {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("postgres:17");

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(DEFAULT_IMAGE_NAME)
		.withDatabaseName("chat_memory_initializer_test")
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcChatMemoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues(String.format("spring.datasource.url=%s", postgresContainer.getJdbcUrl()),
				String.format("spring.datasource.username=%s", postgresContainer.getUsername()),
				String.format("spring.datasource.password=%s", postgresContainer.getPassword()));

	@Test
	void getSettings_shouldHaveSchemaLocations() {
		this.contextRunner.run(context -> {
			var dataSource = context.getBean(DataSource.class);
			var settings = JdbcChatMemoryDataSourceScriptDatabaseInitializer.getSettings(dataSource);

			assertThat(settings.getSchemaLocations())
				.containsOnly("classpath:org/springframework/ai/chat/memory/jdbc/schema-postgresql.sql");
		});
	}

}
