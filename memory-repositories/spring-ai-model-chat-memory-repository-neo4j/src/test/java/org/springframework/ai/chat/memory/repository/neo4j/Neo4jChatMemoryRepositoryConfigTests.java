/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.memory.repository.neo4j;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the indexes {@link Neo4jChatMemoryRepositoryConfig} creates.
 *
 * @author chabinhwang
 */
class Neo4jChatMemoryRepositoryConfigTests {

	@Test
	void indexesThePropertiesTheRepositoryQueries() {

		Session session = mock(Session.class);
		Driver driver = mock(Driver.class);
		given(driver.session()).willReturn(session);

		Neo4jChatMemoryRepositoryConfig.builder().withDriver(driver).build();

		// Sessions are looked up and merged by id, messages are ordered by idx.
		assertThat(indexStatements(session)).satisfiesExactly(
				sessionIndex -> assertThat(sessionIndex).contains("CREATE INDEX session_id_index")
					.contains("FOR (n:Session) ON (n.id)"),
				messageIndex -> assertThat(messageIndex).contains("CREATE INDEX message_idx_index")
					.contains("FOR (n:Message) ON (n.idx)"));
	}

	@Test
	void indexesTheConfiguredLabels() {

		Session session = mock(Session.class);
		Driver driver = mock(Driver.class);
		given(driver.session()).willReturn(session);

		Neo4jChatMemoryRepositoryConfig.builder()
			.withDriver(driver)
			.withSessionLabel("ChatSession")
			.withMessageLabel("ChatMessage")
			.build();

		assertThat(indexStatements(session)).satisfiesExactly(
				sessionIndex -> assertThat(sessionIndex).contains("FOR (n:ChatSession) ON (n.id)"),
				messageIndex -> assertThat(messageIndex).contains("FOR (n:ChatMessage) ON (n.idx)"));
	}

	private static Iterable<String> indexStatements(Session session) {
		ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
		verify(session, times(2)).run(statements.capture());
		return statements.getAllValues();
	}

}
