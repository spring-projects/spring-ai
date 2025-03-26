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

package org.springframework.ai.testcontainers.service.connection.chroma;

import java.util.Map;

import org.testcontainers.chromadb.ChromaDBContainer;

import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 */
class ChromaContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<ChromaDBContainer, ChromaConnectionDetails> {

	@Override
	public ChromaConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<ChromaDBContainer> source) {
		return new ChromaDBContainerConnectionDetails(source);
	}

	/**
	 * {@link ChromaConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class ChromaDBContainerConnectionDetails extends ContainerConnectionDetails<ChromaDBContainer>
			implements ChromaConnectionDetails {

		// Chroma version <= 0.4.x
		private static final String CHROMA_SERVER_AUTH_CREDENTIALS = "CHROMA_SERVER_AUTH_CREDENTIALS";

		// Chroma version >= 0.5.x
		private static final String CHROMA_SERVER_AUTHN_CREDENTIALS = "CHROMA_SERVER_AUTHN_CREDENTIALS";

		private ChromaDBContainerConnectionDetails(ContainerConnectionSource<ChromaDBContainer> source) {
			super(source);
		}

		@Override
		public String getHost() {
			return "http://%s".formatted(getContainer().getHost());
		}

		@Override
		public int getPort() {
			return getContainer().getMappedPort(8000);
		}

		@Override
		public String getKeyToken() {
			Map<String, String> envVars = getContainer().getEnvMap();
			if (envVars.containsKey(CHROMA_SERVER_AUTH_CREDENTIALS)) {
				return envVars.get(CHROMA_SERVER_AUTH_CREDENTIALS);
			}
			return envVars.get(CHROMA_SERVER_AUTHN_CREDENTIALS);
		}

	}

}
