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

package org.springframework.ai.vectorstore.neo4j.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link Neo4jVectorStoreAutoConfiguration} sets the vector size of
 * the store.
 *
 * @author Soby Chacko
 */
class Neo4jVectorStoreAutoConfigurationTests {

	private final Driver driver = mock(Driver.class);

	private final Session session = mock(Session.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Neo4jVectorStoreAutoConfiguration.class))
		.withBean(Driver.class, () -> this.driver)
		.withBean(EmbeddingModel.class, () -> this.embeddingModel);

	@BeforeEach
	void existingIndexOfSize768() {
		when(this.driver.session(any(SessionConfig.class))).thenReturn(this.session);
		when(this.session.executeRead(any())).thenReturn(List.of(Values.value(768)));
	}

	@Test
	void sizeIsReadFromAnExistingIndexWhenNotConfigured() {
		this.contextRunner.run(context -> {
			Neo4jVectorStore store = context.getBean(Neo4jVectorStore.class);

			// The index size, not the size the embedding model reports
			assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(768);
			verifyNoInteractions(this.embeddingModel);
		});
	}

	@Test
	void aConfiguredSizeIsUsedWithoutReadingTheIndex() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.neo4j.embedding-dimension=384").run(context -> {
			Neo4jVectorStore store = context.getBean(Neo4jVectorStore.class);

			assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(384);
			verify(this.session, never()).executeRead(any());
			verifyNoInteractions(this.embeddingModel);
		});
	}

}
