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
package org.springframework.ai.testcontainers.service.connection.typesense;

import org.springframework.ai.autoconfigure.vectorstore.typesense.TypesenseConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.testcontainers.containers.Container;

/**
 * @author Eddú Meléndez
 */
class TypesenseContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<Container<?>, TypesenseConnectionDetails> {

	TypesenseContainerConnectionDetailsFactory() {
		super("typesense/typesense");
	}

	@Override
	protected TypesenseConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<Container<?>> source) {
		return new TypesenseContainerConnectionDetails(source);
	}

	/**
	 * {@link TypesenseConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class TypesenseContainerConnectionDetails extends ContainerConnectionDetails<Container<?>>
			implements TypesenseConnectionDetails {

		private TypesenseContainerConnectionDetails(ContainerConnectionSource<Container<?>> source) {
			super(source);
		}

		@Override
		public String getHost() {
			return getContainer().getHost();
		}

		@Override
		public String getProtocol() {
			return "http";
		}

		@Override
		public int getPort() {
			return getContainer().getMappedPort(8108);
		}

		@Override
		public String getApiKey() {
			return getContainer().getEnvMap().get("TYPESENSE_API_KEY");
		}

	}

}
