/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.cassandra;

import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
public final class SchemaUtil {

	private static final Logger logger = LoggerFactory.getLogger(SchemaUtil.class);

	private SchemaUtil() {

	}

	public static void checkSchemaAgreement(CqlSession session) throws IllegalStateException {
		if (!session.checkSchemaAgreement()) {
			logger.warn("Waiting for cluster schema agreement, sleeping 10s…");
			try {
				Thread.sleep(Duration.ofSeconds(10).toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
			if (!session.checkSchemaAgreement()) {
				logger.error("no cluster schema agreement still, continuing, let's hope this works…");
			}
		}
	}

	public static void ensureKeyspaceExists(CqlSession session, String keyspaceName) {
		if (session.getMetadata().getKeyspace(keyspaceName).isEmpty()) {
			SimpleStatement keyspaceStmt = SchemaBuilder.createKeyspace(keyspaceName)
				.ifNotExists()
				.withSimpleStrategy(1)
				.build();

			logger.debug("Executing {}", keyspaceStmt.getQuery());
			session.execute(keyspaceStmt);
		}
	}

}
