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

import org.springframework.ai.vectorstore.opensearch.autoconfigure.AwsOpenSearchConnectionDetails;
import org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
class AwsOpenSearchDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<AwsOpenSearchConnectionDetails> {

	private static final int LOCALSTACK_PORT = 4566;

	protected AwsOpenSearchDockerComposeConnectionDetailsFactory() {
		super("localstack/localstack");
	}

	@Override
	protected AwsOpenSearchConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new AwsOpenSearchDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link OpenSearchConnectionDetails} backed by a {@code OpenSearch}
	 * {@link RunningService}.
	 */
	static class AwsOpenSearchDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements AwsOpenSearchConnectionDetails {

		private final AwsOpenSearchEnvironment environment;

		private final int port;

		AwsOpenSearchDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.environment = new AwsOpenSearchEnvironment(service.env());
			this.port = service.ports().get(LOCALSTACK_PORT);
		}

		@Override
		public String getRegion() {
			return this.environment.getRegion();
		}

		@Override
		public String getAccessKey() {
			return this.environment.getAccessKey();
		}

		@Override
		public String getSecretKey() {
			return this.environment.getSecretKey();
		}

		@Override
		public String getHost(String domainName) {
			return "%s.%s.opensearch.localhost.localstack.cloud:%s".formatted(domainName, this.environment.getRegion(),
					this.port);
		}

	}

}
