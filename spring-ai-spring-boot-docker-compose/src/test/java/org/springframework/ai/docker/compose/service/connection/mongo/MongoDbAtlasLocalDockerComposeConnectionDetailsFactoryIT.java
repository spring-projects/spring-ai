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

package org.springframework.ai.docker.compose.service.connection.mongo;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIT;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;

import static org.assertj.core.api.Assertions.assertThat;

class MongoDbAtlasLocalDockerComposeConnectionDetailsFactoryIT extends AbstractDockerComposeIT {

	protected MongoDbAtlasLocalDockerComposeConnectionDetailsFactoryIT() {
		super("mongo-compose.yaml", DockerImageName.parse("mongodb/mongodb-atlas-local"));
	}

	@Test
	void runCreatesConnectionDetails() {
		MongoConnectionDetails connectionDetails = run(MongoConnectionDetails.class);
		assertThat(connectionDetails.getConnectionString()).isNotNull();
	}

}
