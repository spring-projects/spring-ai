/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.docker.compose.service.connection.milvus;

import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
class MilvusDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<MilvusServiceClientConnectionDetails> {

	private static final int MILVUS_GRPC_PORT = 19530;

	protected MilvusDockerComposeConnectionDetailsFactory() {
		super("milvusdb/milvus");
	}

	@Override
	protected MilvusServiceClientConnectionDetails getDockerComposeConnectionDetails(
			DockerComposeConnectionSource source) {
		return new MilvusDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link MilvusServiceClientConnectionDetails} backed by a {@code Milvus}
	 * {@link RunningService}.
	 */
	static class MilvusDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements MilvusServiceClientConnectionDetails {

		private final String host;

		private final int port;

		MilvusDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.host = service.host();
			this.port = service.ports().get(MILVUS_GRPC_PORT);
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
