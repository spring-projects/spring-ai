/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.model.bedrock.autoconfigure;

import java.time.Duration;

/**
 * Properties for the synchronous Apache HTTP client.
 *
 * @author Matej Nedic
 */
public class SyncClientProperties {

	/**
	 * Maximum time to wait for a new connection from the pool.
	 */
	private Duration connectionAcquisitionTimeout = Duration.ofSeconds(30L);

	/**
	 * Maximum time to wait while establishing a connection.
	 */
	private Duration connectionTimeout = Duration.ofSeconds(5L);

	/**
	 * Maximum time to wait for response data.
	 */
	private Duration socketTimeout = Duration.ofSeconds(90L);

	public Duration getConnectionAcquisitionTimeout() {
		return this.connectionAcquisitionTimeout;
	}

	public void setConnectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
		this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
	}

	public Duration getConnectionTimeout() {
		return this.connectionTimeout;
	}

	public void setConnectionTimeout(Duration connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public Duration getSocketTimeout() {
		return this.socketTimeout;
	}

	public void setSocketTimeout(Duration socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

}
