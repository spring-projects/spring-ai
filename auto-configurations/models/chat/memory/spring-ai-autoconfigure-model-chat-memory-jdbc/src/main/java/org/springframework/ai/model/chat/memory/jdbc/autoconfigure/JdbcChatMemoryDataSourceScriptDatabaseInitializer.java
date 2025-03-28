package org.springframework.ai.model.chat.memory.jdbc.autoconfigure;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

class JdbcChatMemoryDataSourceScriptDatabaseInitializer extends DataSourceScriptDatabaseInitializer {

	private static final String SCHEMA_LOCATION = "classpath:org/springframework/ai/chat/memory/jdbc/schema-@@platform@@.sql";

	public JdbcChatMemoryDataSourceScriptDatabaseInitializer(DataSource dataSource) {
		super(dataSource, getSettings(dataSource));
	}

	static DatabaseInitializationSettings getSettings(DataSource dataSource) {
		var settings = new DatabaseInitializationSettings();
		settings.setSchemaLocations(resolveSchemaLocations(dataSource));
		settings.setMode(DatabaseInitializationMode.ALWAYS);
		settings.setContinueOnError(true);

		return settings;
	}

	static List<String> resolveSchemaLocations(DataSource dataSource) {
		var platformResolver = new PlatformPlaceholderDatabaseDriverResolver();

		return platformResolver.resolveAll(dataSource, SCHEMA_LOCATION);
	}

}
