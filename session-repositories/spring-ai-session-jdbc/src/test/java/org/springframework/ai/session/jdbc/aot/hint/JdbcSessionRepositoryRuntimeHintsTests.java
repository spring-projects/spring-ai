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

package org.springframework.ai.session.jdbc.aot.hint;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.session.support.SessionEventCodec;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link JdbcSessionRepositoryRuntimeHints}.
 *
 * @author Christian Tzolov
 */
class JdbcSessionRepositoryRuntimeHintsTests {

	private final RuntimeHints hints = new RuntimeHints();

	private final JdbcSessionRepositoryRuntimeHints registrar = new JdbcSessionRepositoryRuntimeHints();

	static Stream<String> schemaFileNames() {
		return Stream.of("schema-h2.sql", "schema-mysql.sql", "schema-postgresql.sql");
	}

	@Test
	void aotFactoriesListRegistrar() {
		boolean listed = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
			.load(RuntimeHintsRegistrar.class)
			.stream()
			.anyMatch(JdbcSessionRepositoryRuntimeHints.class::isInstance);

		assertThat(listed).isTrue();
	}

	@ParameterizedTest
	@MethodSource("schemaFileNames")
	void schemaScriptsHaveResourceHints(String schemaFileName) {
		this.registrar.registerHints(this.hints, getClass().getClassLoader());

		assertThat(
				RuntimeHintsPredicates.resource().forResource("org/springframework/ai/session/jdbc/" + schemaFileName))
			.accepts(this.hints);
	}

	@Test
	void registersWithoutClassLoader() {
		assertThatNoException().isThrownBy(() -> this.registrar.registerHints(this.hints, null));
	}

	@Test
	void messageBodyHasReflectionHints() {
		this.registrar.registerHints(this.hints, getClass().getClassLoader());

		assertThat(RuntimeHintsPredicates.reflection().onType(SessionEventCodec.MessageBody.class)).accepts(this.hints);
	}

}
