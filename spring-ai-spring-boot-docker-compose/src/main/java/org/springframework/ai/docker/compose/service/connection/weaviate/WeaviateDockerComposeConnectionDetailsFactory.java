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

import org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
class WeaviateDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<WeaviateConnectionDetails> {

	private static final String[] WEAVIATE_IMAGE_NAMES = { "semitechnologies/weaviate",
			"cr.weaviate.io/semitechnologies/weaviate" };

	private static final int WEAVIATE_PORT = 8080;

	protected WeaviateDockerComposeConnectionDetailsFactory() {
		super(WEAVIATE_IMAGE_NAMES);
	}

	@Override
	protected WeaviateConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new WeaviateDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link WeaviateConnectionDetails} backed by a {@code Weaviate}
	 * {@link RunningService}.
	 */
	static class WeaviateDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements WeaviateConnectionDetails {

		private final String host;

		WeaviateDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.host = service.host() + ":" + service.ports().get(WEAVIATE_PORT);
		}

		@Override
		public String getHost() {
			return this.host;
		}

	}

}
