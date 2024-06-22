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
package org.springframework.ai.docker.compose.service.connection.chroma;

import java.util.Map;

class ChromaEnvironment {

	// Chroma version <= 0.4.x
	private static final String CHROMA_SERVER_AUTH_CREDENTIALS = "CHROMA_SERVER_AUTH_CREDENTIALS";

	// Chroma version >= 0.5.x
	private static final String CHROMA_SERVER_AUTHN_CREDENTIALS = "CHROMA_SERVER_AUTHN_CREDENTIALS";

	private final String keyToken;

	ChromaEnvironment(Map<String, String> env) {
		if (env.containsKey(CHROMA_SERVER_AUTH_CREDENTIALS)) {
			this.keyToken = env.get(CHROMA_SERVER_AUTH_CREDENTIALS);
			return;
		}
		this.keyToken = env.get(CHROMA_SERVER_AUTHN_CREDENTIALS);
	}

	public String getKeyToken() {
		return this.keyToken;
	}

}
