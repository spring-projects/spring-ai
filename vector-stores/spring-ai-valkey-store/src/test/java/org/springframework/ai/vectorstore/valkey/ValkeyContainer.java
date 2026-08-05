/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.ai.vectorstore.valkey;

import org.testcontainers.containers.GenericContainer;

public final class ValkeyContainer extends GenericContainer<ValkeyContainer> {

	private static final String DEFAULT_IMAGE = "valkey/valkey-bundle:9.0.0-alpine";

	private static final int VALKEY_PORT = 6379;

	public ValkeyContainer(String image) {
		super(image);
		withExposedPorts(VALKEY_PORT);
	}

	public ValkeyContainer() {
		this(DEFAULT_IMAGE);
	}

	public int getPort() {
		return getMappedPort(VALKEY_PORT);
	}

	public String getValkeyUri() {
		return String.format("valkey://%s:%d", getHost(), getPort());
	}

}
