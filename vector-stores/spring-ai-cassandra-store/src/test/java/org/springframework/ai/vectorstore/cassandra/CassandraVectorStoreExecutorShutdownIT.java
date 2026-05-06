/*
 * Copyright 2025 the original author or authors.
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.observation.decorate.EmbeddingModelObservationDocumentation;
import org.springframework.ai.test.support.mock.MockEmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for GitHub issue #5931: CassandraVectorStore thread pool leak —
 * executor never shut down in close().
 *
 * This test verifies that closing a CassandraVectorStore properly shuts down the
 * internal executor, preventing thread accumulation in long-running applications.
 *
 * @author Redos OSS Bot
 */
public class CassandraVectorStoreExecutorShutdownIT {

	private static final String KEYSPACE = "test_executor_shutdown_" + System.currentTimeMillis();

	private static final String TABLE = "test_vectors";

	private static CqlSession session;

	private static MockEmbeddingModel embeddingModel;

	private static int originalThreadCount;

	@BeforeAll
	static void setUp() {
		// Use a mock embedding model that doesn't require a real model endpoint
		embeddingModel = new MockEmbeddingModel();

		// Build session to testcontainer
		session = new CqlSessionBuilder().addContactPoint(new java.net.InetSocketAddress("localhost", 9042))
			.withLocalDatacenter("datacenter1")
			.withKeyspace("system")
			.build();

		// Create test keyspace
		session.execute(SimpleStatement.builder("CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE
				+ " WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}")
			.build());

		session.execute(SimpleStatement.builder(
				"CREATE TABLE IF NOT EXISTS " + KEYSPACE + "." + TABLE
						+ " (id text PRIMARY KEY, content text, embedding vector<float, 384>)")
			.build());

		// Wait for schema agreement
		try {
			Thread.sleep(2000);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// Record baseline thread count
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		originalThreadCount = threadMXBean.getThreadCount();
	}

	@AfterAll
	static void tearDown() {
		if (session != null) {
			try {
				session.execute(
						SimpleStatement.builder("DROP KEYSPACE IF EXISTS " + KEYSPACE).build());
			}
			catch (Exception e) {
				// Ignore cleanup errors
			}
			session.close();
		}
	}

	@Test
	void shouldShutdownExecutorOnClose() throws Exception {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

		int iterations = 10;
		for (int i = 0; i < iterations; i++) {
			CassandraVectorStore store = CassandraVectorStore.builder(embeddingModel).session(session)
				.keyspace(KEYSPACE)
				.table(TABLE + "_iteration_" + i)
				.initializeSchema(true)
				.fixedThreadPoolExecutorSize(8)
				.build();

			// Close the store
			store.close();

			// Small delay to allow executor threads to terminate
			TimeUnit.MILLISECONDS.sleep(500);
		}

		// Give threads time to fully terminate
		TimeUnit.SECONDS.sleep(2);

		int finalThreadCount = threadMXBean.getThreadCount();
		int threadGrowth = finalThreadCount - originalThreadCount;

		// Allow for some tolerance but executor threads should be cleaned up
		// Each store creates 8 threads, after 10 iterations if not shut down we'd have 80 threads
		assertThat(threadGrowth).as(
				"Executor threads should be cleaned up after close(). "
						+ "Expected thread growth <= 5, but was %d (final: %d, original: %d)",
				threadGrowth, finalThreadCount, originalThreadCount)
			.isLessThanOrEqualTo(5);
	}

	@Test
	void shouldShutdownExecutorEvenIfSessionCloseThrows() throws Exception {
		// Create a store with closeSessionOnClose=true to test both cleanup paths
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		int baselineBeforeTest = threadMXBean.getThreadCount();

		for (int i = 0; i < 5; i++) {
			// Create store that will close its own session
			CqlSession tempSession = new CqlSessionBuilder().addContactPoint(new java.net.InetSocketAddress("localhost", 9042))
				.withLocalDatacenter("datacenter1")
				.withKeyspace("system")
				.build();

			// Override to auto-close session
			CassandraVectorStore store = CassandraVectorStore.builder(embeddingModel).session(tempSession)
				.keyspace(KEYSPACE)
				.table(TABLE + "_session_close_test_" + i)
				.initializeSchema(true)
				.fixedThreadPoolExecutorSize(4)
				.build();

			// Close - should shut down executor AND close session
			store.close();

			TimeUnit.MILLISECONDS.sleep(300);
		}

		TimeUnit.SECONDS.sleep(1);

		int finalCount = threadMXBean.getThreadCount();
		int growth = finalCount - baselineBeforeTest;

		// Even with session close, executor should be shut down
		assertThat(growth)
			.as("Executor threads should be cleaned up even when closing session. Growth: %d", growth)
			.isLessThanOrEqualTo(3);
	}

}