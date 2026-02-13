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

import java.util.List;

import org.opensearch.testcontainers.OpenSearchContainer;

import org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 */
class OpenSearchContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<OpenSearchContainer<?>, OpenSearchConnectionDetails> {

	@Override
	public OpenSearchConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<OpenSearchContainer<?>> source) {
		return new OpenSearchContainerConnectionDetails(source);
	}

	/**
	 * {@link OpenSearchConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class OpenSearchContainerConnectionDetails
			extends ContainerConnectionDetails<OpenSearchContainer<?>> implements OpenSearchConnectionDetails {

		private OpenSearchContainerConnectionDetails(ContainerConnectionSource<OpenSearchContainer<?>> source) {
			super(source);
		}

		@Override
		public List<String> getUris() {
			return List.of(getContainer().getHttpHostAddress());
		}

		@Override
		public String getUsername() {
			return getContainer().isSecurityEnabled() ? getContainer().getUsername() : null;
		}

		@Override
		public String getPassword() {
			return getContainer().isSecurityEnabled() ? getContainer().getPassword() : null;
		}

	}

}
