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

package org.springframework.ai.docker.compose.service.connection.weaviate;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIT;

import static org.assertj.core.api.Assertions.assertThat;

class WeaviateDockerComposeConnectionDetailsFactoryIT extends AbstractDockerComposeIT {

	WeaviateDockerComposeConnectionDetailsFactoryIT() {
		super("weaviate-compose.yaml", DockerImageName.parse("semitechnologies/weaviate"));
	}

	@Test
	void runCreatesConnectionDetails() {
		WeaviateConnectionDetails connectionDetails = run(WeaviateConnectionDetails.class);
		assertThat(connectionDetails.getHost()).isNotNull();
	}

}
