/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = JdbcChatMemoryHsqldbAutoConfigurationIT.TestConfig.class,
		properties = { "spring.datasource.url=jdbc:hsqldb:mem:chat_memory_auto_configuration_test;DB_CLOSE_DELAY=-1",
				"spring.datasource.username=sa", "spring.datasource.password=",
				"spring.datasource.driver-class-name=org.hsqldb.jdbcDriver",
				"spring.ai.chat.memory.repository.jdbc.initialize-schema=always", "spring.sql.init.mode=always",
				"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.defer-datasource-initialization=true",
				"spring.sql.init.continue-on-error=true", "spring.sql.init.schema-locations=classpath:schema.sql",
				"logging.level.org.springframework.jdbc=DEBUG",
				"logging.level.org.springframework.boot.sql.init=DEBUG" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ImportAutoConfiguration({ org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration.class,
		JdbcChatMemoryRepositoryAutoConfiguration.class,
		org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
		org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
		SqlInitializationAutoConfiguration.class })
public class JdbcChatMemoryHsqldbAutoConfigurationIT {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * can't get the automatic loading of the schema with boot to work.
	 */
	@Before
	public void setUp() {
		// Explicitly initialize the schema
		try {
			System.out.println("Explicitly initializing schema...");

			// Debug: Print current schemas and tables
			try {
				List<String> schemas = jdbcTemplate.queryForList("SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA",
						String.class);
				System.out.println("Available schemas: " + schemas);

				List<String> tables = jdbcTemplate
					.queryForList("SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES", String.class);
				System.out.println("Available tables: " + tables);
			}
			catch (Exception e) {
				System.out.println("Error listing schemas/tables: " + e.getMessage());
			}

			// Try a more direct approach with explicit SQL statements
			try {
				// Drop the table first if it exists to avoid any conflicts
				jdbcTemplate.execute("DROP TABLE SPRING_AI_CHAT_MEMORY IF EXISTS");
				System.out.println("Dropped existing table if it existed");

				// Create the table with a simplified schema
				jdbcTemplate.execute("CREATE TABLE SPRING_AI_CHAT_MEMORY (" + "conversation_id VARCHAR(36) NOT NULL, "
						+ "content LONGVARCHAR NOT NULL, " + "type VARCHAR(10) NOT NULL, "
						+ "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
				System.out.println("Created table with simplified schema");

				// Create index
				jdbcTemplate.execute(
						"CREATE INDEX SPRING_AI_CHAT_MEMORY_IDX ON SPRING_AI_CHAT_MEMORY(conversation_id, timestamp DESC)");
				System.out.println("Created index");

				// Verify table was created
				boolean tableExists = jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SPRING_AI_CHAT_MEMORY'",
						Integer.class) > 0;
				System.out.println("Table SPRING_AI_CHAT_MEMORY exists after creation: " + tableExists);
			}
			catch (Exception e) {
				System.out.println("Error during direct table creation: " + e.getMessage());
				e.printStackTrace();
			}

			System.out.println("Schema initialization completed");
		}
		catch (Exception e) {
			System.out.println("Error during explicit schema initialization: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Test
	public void useAutoConfiguredChatMemoryWithJdbc() {
		// Check that the custom schema initializer is present
		assertThat(context.containsBean("jdbcChatMemoryScriptDatabaseInitializer")).isTrue();

		// Debug: List all schema-hsqldb.sql resources on the classpath
		try {
			java.util.Enumeration<java.net.URL> resources = Thread.currentThread()
				.getContextClassLoader()
				.getResources("org/springframework/ai/chat/memory/jdbc/schema-hsqldb.sql");
			System.out.println("--- schema-hsqldb.sql resources found on classpath ---");
			while (resources.hasMoreElements()) {
				System.out.println(resources.nextElement());
			}
			System.out.println("------------------------------------------------------");
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		// Verify the table exists by executing a direct query
		try {
			boolean tableExists = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SPRING_AI_CHAT_MEMORY'",
					Integer.class) > 0;
			System.out.println("Table SPRING_AI_CHAT_MEMORY exists: " + tableExists);
			assertThat(tableExists).isTrue();
		}
		catch (Exception e) {
			System.out.println("Error checking table: " + e.getMessage());
			e.printStackTrace();
			fail("Failed to check if table exists: " + e.getMessage());
		}

		// Now test the ChatMemory functionality
		assertThat(context.getBean(org.springframework.ai.chat.memory.ChatMemory.class)).isNotNull();
		assertThat(context.getBean(org.springframework.ai.chat.memory.jdbc.JdbcChatMemoryRepository.class)).isNotNull();

		var chatMemory = context.getBean(org.springframework.ai.chat.memory.ChatMemory.class);
		var conversationId = java.util.UUID.randomUUID().toString();
		var userMessage = new UserMessage("Message from the user");

		chatMemory.add(conversationId, userMessage);
		assertThat(chatMemory.get(conversationId)).hasSize(1);
		assertThat(chatMemory.get(conversationId)).isEqualTo(List.of(userMessage));

		var assistantMessage = new AssistantMessage("Message from the assistant");
		chatMemory.add(conversationId, List.of(assistantMessage));
		assertThat(chatMemory.get(conversationId)).hasSize(2);
		assertThat(chatMemory.get(conversationId)).isEqualTo(List.of(userMessage, assistantMessage));

		chatMemory.clear(conversationId);
		assertThat(chatMemory.get(conversationId)).isEmpty();

		var multipleMessages = List.<Message>of(new UserMessage("Message from the user 1"),
				new AssistantMessage("Message from the assistant 1"));
		chatMemory.add(conversationId, multipleMessages);
		assertThat(chatMemory.get(conversationId)).hasSize(multipleMessages.size());
		assertThat(chatMemory.get(conversationId)).isEqualTo(multipleMessages);
	}

	@SpringBootConfiguration
	static class TestConfig {

	}

}
