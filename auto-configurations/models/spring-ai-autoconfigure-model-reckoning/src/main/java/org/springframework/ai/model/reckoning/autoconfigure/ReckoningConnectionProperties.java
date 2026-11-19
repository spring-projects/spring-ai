/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.reckoning.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection properties for Reckoning.
 *
 * @since 2.1.0
 */
@ConfigurationProperties(ReckoningConnectionProperties.CONFIG_PREFIX)
@SuppressWarnings("NullAway")
public class ReckoningConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.reckoning";

	private String baseUrl = "http://localhost:8080";

	private String username;

	private String password;

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}
