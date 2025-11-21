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

import com.mongodb.ConnectionString;

import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;

/**
 * A {@link DockerComposeConnectionDetailsFactory} implementation that creates
 * {@link MongoConnectionDetails} for a MongoDB Atlas Local instance running in a Docker
 * container.
 *
 * <p>
 * This factory is designed to work with Docker Compose configurations that include a
 * MongoDB Atlas Local container. It provides the necessary connection details for Spring
 * Boot applications to connect to the MongoDB instance.
 *
 * <p>
 * The factory matches containers with the image name "mongodb/mongodb-atlas-local".
 *
 * <p>
 * Usage of this factory requires the presence of the Spring Boot Docker Compose support
 * and the MongoDB driver on the classpath.
 *
 * @author Eddú Meléndez
 * @see DockerComposeConnectionDetailsFactory
 * @see MongoConnectionDetails
 * @see org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource
 * @since 1.0.0
 */
class MongoDbAtlasLocalDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<MongoConnectionDetails> {

	private static final int MONGODB_PORT = 27017;

	protected MongoDbAtlasLocalDockerComposeConnectionDetailsFactory() {
		super("mongodb/mongodb-atlas-local");
	}

	@Override
	protected MongoConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new MongoDbAtlasLocalContainerConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link MongoConnectionDetails} backed by a {@code MongoDB Atlas}
	 * {@link RunningService}.
	 */
	static class MongoDbAtlasLocalContainerConnectionDetails extends DockerComposeConnectionDetails
			implements MongoConnectionDetails {

		private final String connectionString;

		MongoDbAtlasLocalContainerConnectionDetails(RunningService service) {
			super(service);
			this.connectionString = String.format("mongodb://%s:%d/?directConnection=true", service.host(),
					service.ports().get(MONGODB_PORT));
		}

		@Override
		public ConnectionString getConnectionString() {
			return new ConnectionString(this.connectionString);
		}

	}

}
