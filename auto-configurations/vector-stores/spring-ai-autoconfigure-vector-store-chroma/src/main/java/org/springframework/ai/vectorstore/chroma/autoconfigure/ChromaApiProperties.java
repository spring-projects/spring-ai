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

package org.springframework.ai.vectorstore.chroma.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Chroma API client.
 *
 * @author Christian Tzolov
 */
@ConfigurationProperties(ChromaApiProperties.CONFIG_PREFIX)
public class ChromaApiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.chroma.client";

	private String host = "http://localhost";

	private int port = 8000;

	private @Nullable String keyToken;

	private @Nullable String username;

	private @Nullable String password;

	public String getHost() {
		return this.host;
	}

	public void setHost(String baseUrl) {
		this.host = baseUrl;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public @Nullable String getKeyToken() {
		return this.keyToken;
	}

	public void setKeyToken(@Nullable String keyToken) {
		this.keyToken = keyToken;
	}

	public @Nullable String getUsername() {
		return this.username;
	}

	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	public @Nullable String getPassword() {
		return this.password;
	}

	public void setPassword(@Nullable String password) {
		this.password = password;
	}

}
