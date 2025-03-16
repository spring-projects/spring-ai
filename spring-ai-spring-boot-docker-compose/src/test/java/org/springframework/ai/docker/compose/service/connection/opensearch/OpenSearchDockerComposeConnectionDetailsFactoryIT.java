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

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.autoconfigure.vectorstore.opensearch.OpenSearchConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIT;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchDockerComposeConnectionDetailsFactoryIT extends AbstractDockerComposeIT {

	OpenSearchDockerComposeConnectionDetailsFactoryIT() {
		super("opensearch-compose.yaml", DockerImageName.parse("opensearchproject/opensearch"));
	}

	@Test
	void runCreatesConnectionDetails() {
		OpenSearchConnectionDetails connectionDetails = run(OpenSearchConnectionDetails.class);
		assertThat(connectionDetails.getUris()).isNotNull();
		assertThat(connectionDetails.getUsername()).isEqualTo("admin");
		assertThat(connectionDetails.getPassword()).isEqualTo("D3v3l0p-ment");
	}

}
