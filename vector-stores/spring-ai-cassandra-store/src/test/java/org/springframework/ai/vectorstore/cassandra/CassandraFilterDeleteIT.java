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

package org.springframework.ai.vectorstore.cassandra;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumnTags;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
class CassandraFilterDeleteIT {

	@Container
	static CassandraContainer cassandra = new CassandraContainer(CassandraImage.DEFAULT_IMAGE)
		.withEnv("MAX_HEAP_SIZE", "512M")
		.withEnv("HEAP_NEWSIZE", "100M");

	@Test
	void filterDeleteRemovesMatchesAcrossDriverPages() throws Exception {
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(3);
		when(embeddingModel.embed("")).thenReturn(new float[] { 1, 0, 0 });

		try (CqlSession session = CqlSession.builder()
			.addContactPoint(new InetSocketAddress(cassandra.getHost(), cassandra.getMappedPort(9042)))
			.withLocalDatacenter(cassandra.getLocalDatacenter())
			.withConfigLoader(DriverConfigLoader.programmaticBuilder()
				.withInt(DefaultDriverOption.REQUEST_PAGE_SIZE, 37)
				.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(20))
				.startProfile(CassandraVectorStore.DRIVER_PROFILE_SEARCH)
				.withInt(DefaultDriverOption.REQUEST_PAGE_SIZE, 37)
				.endProfile()
				.startProfile(CassandraVectorStore.DRIVER_PROFILE_UPDATES)
				.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(20))
				.endProfile()
				.build())
			.build();
				CassandraVectorStore store = CassandraVectorStore.builder(embeddingModel)
					.session(session)
					.keyspace("test_filter_delete")
					.table("documents")
					.addMetadataColumns(new SchemaColumn("category", DataTypes.TEXT, SchemaColumnTags.INDEXED))
					.build()) {

			var insert = session.prepare("INSERT INTO test_filter_delete.documents "
					+ "(id, content, embedding, category) VALUES (?, 'content', [1, 0, 0], ?)");
			var writes = new ArrayList<CompletableFuture<?>>();
			for (int i = 0; i < 1001; i++) {
				writes.add(session.executeAsync(insert.bind("obsolete-" + i, "obsolete")).toCompletableFuture());
			}
			writes.add(session.executeAsync(insert.bind("retained", "current")).toCompletableFuture());
			CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

			store.delete(new FilterExpressionBuilder().eq("category", "obsolete").build());

			assertThat(session.execute("SELECT id FROM test_filter_delete.documents").all())
				.extracting(row -> row.getString("id"))
				.containsExactly("retained");
			verify(embeddingModel, never()).embed(anyString());
		}
	}

}
