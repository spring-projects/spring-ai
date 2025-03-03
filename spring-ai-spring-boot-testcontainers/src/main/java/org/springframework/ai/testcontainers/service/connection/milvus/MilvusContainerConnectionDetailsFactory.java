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

package org.springframework.ai.testcontainers.service.connection.milvus;

import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 */
class MilvusContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<MilvusContainer, MilvusServiceClientConnectionDetails> {

	@Override
	public MilvusServiceClientConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<MilvusContainer> source) {
		return new MilvusContainerConnectionDetails(source);
	}

	/**
	 * {@link MilvusServiceClientConnectionDetails} backed by a
	 * {@link ContainerConnectionSource}.
	 */
	private static final class MilvusContainerConnectionDetails extends ContainerConnectionDetails<MilvusContainer>
			implements MilvusServiceClientConnectionDetails {

		private MilvusContainerConnectionDetails(ContainerConnectionSource<MilvusContainer> source) {
			super(source);
		}

		@Override
		public String getHost() {
			return getContainer().getHost();
		}

		@Override
		public int getPort() {
			return getContainer().getMappedPort(19530);
		}

	}

}
