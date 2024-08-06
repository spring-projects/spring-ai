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

import org.springframework.ai.autoconfigure.vectorstore.chroma.ChromaConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
class ChromaDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<ChromaConnectionDetails> {

	private static final String[] CHROMA_IMAGE_NAMES = { "chromadb/chroma", "ghcr.io/chroma-core/chroma" };

	private static final int CHROMA_PORT = 8000;

	protected ChromaDockerComposeConnectionDetailsFactory() {
		super(CHROMA_IMAGE_NAMES);
	}

	@Override
	protected ChromaConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new ChromaDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link ChromaConnectionDetails} backed by a {@code Chroma} {@link RunningService}.
	 */
	static class ChromaDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements ChromaConnectionDetails {

		private final ChromaEnvironment environment;

		private final String host;

		private final int port;

		ChromaDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.environment = new ChromaEnvironment(service.env());
			this.host = service.host();
			this.port = service.ports().get(CHROMA_PORT);
		}

		@Override
		public String getHost() {
			return this.host;
		}

		@Override
		public int getPort() {
			return this.port;
		}

		@Override
		public String getKeyToken() {
			return this.environment.getKeyToken();
		}

	}

}
