/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.docker.compose.service.connection.chroma;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChromaEnvironmentTests {

	@Test
	void getKeyTokenWhenNoCredential() {
		ChromaEnvironment environment = new ChromaEnvironment(Collections.emptyMap());
		assertThat(environment.getKeyToken()).isNull();
	}

	@Test
	void getKeyTokenWhenHasCredential() {
		ChromaEnvironment environment = new ChromaEnvironment(Map.of("CHROMA_SERVER_AUTHN_CREDENTIALS", "secret"));
		assertThat(environment.getKeyToken()).isEqualTo("secret");
	}

}
