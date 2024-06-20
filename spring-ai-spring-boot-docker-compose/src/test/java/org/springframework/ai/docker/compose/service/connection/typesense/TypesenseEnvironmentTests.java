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
package org.springframework.ai.docker.compose.service.connection.typesense;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypesenseEnvironmentTests {

	@Test
	void getApiKeyWhenNoApiKey() {
		TypesenseEnvironment environment = new TypesenseEnvironment(Collections.emptyMap());
		assertThat(environment.getApiKey()).isNull();
	}

	@Test
	void getApiKeyWhenHasApiKey() {
		TypesenseEnvironment environment = new TypesenseEnvironment(Map.of("TYPESENSE_API_KEY", "secret"));
		assertThat(environment.getApiKey()).isEqualTo("secret");
	}

}
