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

package org.springframework.ai.docker.compose.service.connection.opensearch;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchEnvironmentTests {

	@Test
	void getPasswordWhenNoPassword() {
		OpenSearchEnvironment environment = new OpenSearchEnvironment(Collections.emptyMap());
		assertThat(environment.getPassword()).isNull();
	}

	@Test
	void getPasswordWhenHasPassword() {
		OpenSearchEnvironment environment = new OpenSearchEnvironment(
				Map.of("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "secret"));
		assertThat(environment.getPassword()).isEqualTo("secret");
	}

}
