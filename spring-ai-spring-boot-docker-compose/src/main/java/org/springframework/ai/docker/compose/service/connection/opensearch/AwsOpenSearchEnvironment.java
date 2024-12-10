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

package org.springframework.ai.docker.compose.service.connection.opensearch;

import java.util.Map;

class AwsOpenSearchEnvironment {

	private final String region;

	private final String accessKey;

	private final String secretKey;

	AwsOpenSearchEnvironment(Map<String, String> env) {
		this.region = env.getOrDefault("DEFAULT_REGION", "us-east-1");
		this.accessKey = env.getOrDefault("AWS_ACCESS_KEY_ID", "test");
		this.secretKey = env.getOrDefault("AWS_SECRET_ACCESS_KEY", "test");
	}

	public String getRegion() {
		return this.region;
	}

	public String getAccessKey() {
		return this.accessKey;
	}

	public String getSecretKey() {
		return this.secretKey;
	}

}
