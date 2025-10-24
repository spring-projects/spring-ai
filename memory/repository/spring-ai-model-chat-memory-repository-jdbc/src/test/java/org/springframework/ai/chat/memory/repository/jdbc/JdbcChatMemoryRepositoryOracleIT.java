package org.springframework.ai.chat.memory.repository.jdbc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:oracle:thin:@localhost:1521/FREEPDB1",
		"spring.datasource.username=scott",
		"spring.datasource.password=tiger"})

@Sql(scripts = "classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-oracle.sql")
class JdbcChatMemoryRepositoryOracleIT extends AbstractJdbcChatMemoryRepositoryIT {

}
