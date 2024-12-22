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

package org.springframework.ai.docker.compose.service.connection.typesense;

import org.springframework.ai.autoconfigure.vectorstore.typesense.TypesenseConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * {@link DockerComposeConnectionDetailsFactory} for {@link TypesenseConnectionDetails}.
 *
 * @author Eddú Meléndez
 */
public class TypesenseDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<TypesenseConnectionDetails> {

	private static final int TYPESENSE_PORT = 8108;

	protected TypesenseDockerComposeConnectionDetailsFactory() {
		super("typesense/typesense");
	}

	@Override
	protected TypesenseConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new TypesenseComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link TypesenseConnectionDetails} backed by a {@code Typesense}
	 * {@link RunningService}.
	 */
	static class TypesenseComposeConnectionDetails extends DockerComposeConnectionDetails
			implements TypesenseConnectionDetails {

		private final TypesenseEnvironment environment;

		private final String host;

		private final int port;

		TypesenseComposeConnectionDetails(RunningService service) {
			super(service);
			this.environment = new TypesenseEnvironment(service.env());
			this.host = service.host();
			this.port = service.ports().get(TYPESENSE_PORT);
		}

		@Override
		public String getHost() {
			return this.host;
		}

		@Override
		public String getProtocol() {
			return "http";
		}

		@Override
		public int getPort() {
			return this.port;
		}

		@Override
		public String getApiKey() {
			return this.environment.getApiKey();
		}

	}

}
