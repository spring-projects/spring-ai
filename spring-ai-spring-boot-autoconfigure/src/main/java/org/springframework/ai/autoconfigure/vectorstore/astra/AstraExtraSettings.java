/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.astra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import org.springframework.lang.Nullable;

/**
 * Example properties to enable a CQL connection to an AstraDB using a secure
 * secureConnectBundle file <code>
# Cassandra Configuration
spring.cassandra.keyspace-name=default_keyspace
spring.cassandra.username=token
spring.cassandra.password=${ASTRA_DB_APPLICATION_TOKEN}
spring.ai.vectorstore.astra.secure-connect-bundle=${ASTRA_SCB_PATH} </code>
 *
 * @author Cédrick Lunven
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.vectorstore.astra")
public class AstraExtraSettings {

	@Nullable // null means vanilla CQL (i.e. AstraDB not enabled)
	private File secureConnectBundle;

	public File getSecureConnectBundle() {
		return secureConnectBundle;
	}

	public AstraExtraSettings setSecureConnectBundle(File secureConnectBundle) {
		this.secureConnectBundle = secureConnectBundle;
		return this;
	}

}