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

import java.util.List;

import org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
class OpenSearchDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<OpenSearchConnectionDetails> {

	private static final int OPENSEARCH_PORT = 9200;

	protected OpenSearchDockerComposeConnectionDetailsFactory() {
		super("opensearchproject/opensearch");
	}

	@Override
	protected OpenSearchConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new OpenSearchDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link OpenSearchConnectionDetails} backed by a {@code OpenSearch}
	 * {@link RunningService}.
	 */
	static class OpenSearchDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements OpenSearchConnectionDetails {

		private final OpenSearchEnvironment environment;

		private final String uri;

		OpenSearchDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.environment = new OpenSearchEnvironment(service.env());
			this.uri = "http://" + service.host() + ":" + service.ports().get(OPENSEARCH_PORT);
		}

		@Override
		public List<String> getUris() {
			return List.of(this.uri);
		}

		@Override
		public String getUsername() {
			return "admin";
		}

		@Override
		public String getPassword() {
			return this.environment.getPassword();
		}

	}

}
