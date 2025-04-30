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

package org.springframework.ai.testcontainers.service.connection.opensearch;

import org.testcontainers.containers.localstack.LocalStackContainer;

import org.springframework.ai.vectorstore.opensearch.autoconfigure.AwsOpenSearchConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 */
class AwsOpenSearchContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<LocalStackContainer, AwsOpenSearchConnectionDetails> {

	@Override
	public AwsOpenSearchConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<LocalStackContainer> source) {
		return new AwsOpenSearchContainerConnectionDetails(source);
	}

	/**
	 * {@link AwsOpenSearchConnectionDetails} backed by a
	 * {@link ContainerConnectionSource}.
	 */
	private static final class AwsOpenSearchContainerConnectionDetails
			extends ContainerConnectionDetails<LocalStackContainer> implements AwsOpenSearchConnectionDetails {

		private AwsOpenSearchContainerConnectionDetails(ContainerConnectionSource<LocalStackContainer> source) {
			super(source);
		}

		@Override
		public String getRegion() {
			return getContainer().getRegion();
		}

		@Override
		public String getAccessKey() {
			return getContainer().getAccessKey();
		}

		@Override
		public String getSecretKey() {
			return getContainer().getSecretKey();
		}

		@Override
		public String getHost(String domainName) {
			return "%s.%s.opensearch.localhost.localstack.cloud:%s".formatted(domainName, getContainer().getRegion(),
					getContainer().getMappedPort(4566));
		}

	}

}
