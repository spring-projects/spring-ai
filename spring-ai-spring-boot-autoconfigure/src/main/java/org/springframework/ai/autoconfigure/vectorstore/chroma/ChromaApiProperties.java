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
package org.springframework.ai.autoconfigure.vectorstore.chroma;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(ChromaApiProperties.CONFIG_PREFIX)
public class ChromaApiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.chroma.client";

	private String host = "http://localhost";

	private int port = 8000;

	private String keyToken;

	private String username;

	private String password;

	public String getHost() {
		return host;
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

	public String getKeyToken() {
		return this.keyToken;
	}

	public void setKeyToken(String keyToken) {
		this.keyToken = keyToken;
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
