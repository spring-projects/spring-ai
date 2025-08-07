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

package org.springframework.ai.chat.memory.repository.jdbc.aot.hint;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * @author Jonathan Leijendekker
 */
class JdbcChatMemoryRepositoryRuntimeHintsTest {

	private final RuntimeHints hints = new RuntimeHints();

	private final JdbcChatMemoryRepositoryRuntimeHints jdbcChatMemoryRepositoryRuntimeHints = new JdbcChatMemoryRepositoryRuntimeHints();

	@Test
	void aotFactoriesContainsRegistrar() {
		var match = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
			.load(RuntimeHintsRegistrar.class)
			.stream()
			.anyMatch(registrar -> registrar instanceof JdbcChatMemoryRepositoryRuntimeHints);

		assertThat(match).isTrue();
	}

	@ParameterizedTest
	@MethodSource("getSchemaFileNames")
	void jdbcSchemasHasHints(String schemaFileName) {
		this.jdbcChatMemoryRepositoryRuntimeHints.registerHints(this.hints, getClass().getClassLoader());

		var predicate = RuntimeHintsPredicates.resource()
			.forResource("org/springframework/ai/chat/memory/repository/jdbc/" + schemaFileName);

		assertThat(predicate).accepts(this.hints);
	}

	@Test
	void dataSourceHasHints() {
		this.jdbcChatMemoryRepositoryRuntimeHints.registerHints(this.hints, getClass().getClassLoader());

		assertThat(RuntimeHintsPredicates.reflection().onType(DataSource.class)).accepts(this.hints);
	}

	@Test
	void registerHintsWithNullClassLoader() {
		assertThatNoException()
			.isThrownBy(() -> this.jdbcChatMemoryRepositoryRuntimeHints.registerHints(this.hints, null));
	}

	private static Stream<String> getSchemaFileNames() throws IOException {
		var resources = new PathMatchingResourcePatternResolver()
			.getResources("classpath*:org/springframework/ai/chat/memory/repository/jdbc/schema-*.sql");

		return Arrays.stream(resources).map(Resource::getFilename);
	}

}
