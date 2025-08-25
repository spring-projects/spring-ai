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

package org.springframework.ai.docker.compose.service.connection.elasticsearch;

import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

import java.util.List;

/**
 * A {@link DockerComposeConnectionDetailsFactory} implementation that creates
 * {@link ElasticsearchConnectionDetails} for an Elasticsearch instance running in a
 * Docker container.
 *
 * @author Laura Trotta
 * @see DockerComposeConnectionDetailsFactory
 * @see ElasticsearchConnectionDetails
 * @see DockerComposeConnectionSource
 * @since 1.0.2
 */
class ElasticsearchDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<ElasticsearchConnectionDetails> {

	private static final int ELASTICSEARCH_PORT = 9200;

	protected ElasticsearchDockerComposeConnectionDetailsFactory() {
		super("docker.elastic.co/elasticsearch/elasticsearch");
	}

	@Override
	protected ElasticsearchConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new ElasticsearchContainerConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link ElasticsearchConnectionDetails} backed by an {@code Elasticsearch}
	 * {@link RunningService}.
	 */
	static class ElasticsearchContainerConnectionDetails extends DockerComposeConnectionDetails
			implements ElasticsearchConnectionDetails {

		private final ElasticsearchEnvironment environment;

		private final String host;

		ElasticsearchContainerConnectionDetails(RunningService service) {
			super(service);
			this.environment = new ElasticsearchEnvironment(service.env());
			this.host = service.host();
		}

		@Override
		public List<Node> getNodes() {
			return List.of(new Node(host, ELASTICSEARCH_PORT, Node.Protocol.HTTPS, getUsername(), getPassword()));
		}

		@Override
		public String getUsername() {
			return "elastic";
		}

		@Override
		public String getPassword() {
			return environment.getPassword();
		}

		@Override
		public String getPathPrefix() {
			return "";
		}

	}

}
