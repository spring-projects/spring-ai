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

package org.springframework.ai.openai.batch.repository.jdbc.aot.hint;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * A {@link RuntimeHintsRegistrar} for JDBC Batch Execution Repository hints.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
class JdbcBatchExecutionRepositoryRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		hints.reflection()
			.registerType(DataSource.class, hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));

		hints.resources().registerPattern("org/springframework/ai/openai/batch/repository/jdbc/schema-*.sql");
	}

}
