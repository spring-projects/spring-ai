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
package org.springframework.ai.docker.compose.service.connection.qdrant;

import org.springframework.ai.autoconfigure.vectorstore.qdrant.QdrantConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
public class QdrantDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<QdrantConnectionDetails> {

	private static final int QDRANT_GRPC_PORT = 6334;

	protected QdrantDockerComposeConnectionDetailsFactory() {
		super("qdrant/qdrant");
	}

	@Override
	protected QdrantConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new QdrantDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link QdrantConnectionDetails} backed by a {@code Qdrant} {@link RunningService}.
	 */
	static class QdrantDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements QdrantConnectionDetails {

		private final String host;

		private final int port;

		QdrantDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.host = service.host();
			this.port = service.ports().get(QDRANT_GRPC_PORT);
		}

		@Override
		public String getHost() {
			return this.host;
		}

		@Override
		public int getPort() {
			return this.port;
		}

	}

}
