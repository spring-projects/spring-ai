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

package org.springframework.ai.testcontainers.service.connection.qdrant;

import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 */
class QdrantContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<QdrantContainer, QdrantConnectionDetails> {

	@Override
	public QdrantConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<QdrantContainer> source) {
		return new QdrantContainerConnectionDetails(source);
	}

	/**
	 * {@link QdrantConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class QdrantContainerConnectionDetails extends ContainerConnectionDetails<QdrantContainer>
			implements QdrantConnectionDetails {

		private QdrantContainerConnectionDetails(ContainerConnectionSource<QdrantContainer> source) {
			super(source);
		}

		@Override
		public String getHost() {
			return getContainer().getHost();
		}

		@Override
		public int getPort() {
			return getContainer().getMappedPort(6334);
		}

		@Override
		public String getApiKey() {
			return getContainer().getEnvMap().get("QDRANT__SERVICE__API_KEY");
		}

	}

}
