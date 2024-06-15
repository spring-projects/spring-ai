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
package org.springframework.ai.docker.compose.service.connection.redis;

import org.springframework.ai.autoconfigure.vectorstore.redis.RedisConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 */
public class RedisDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<RedisConnectionDetails> {

	private static final int REDIS_PORT = 6379;

	protected RedisDockerComposeConnectionDetailsFactory() {
		super("redis/redis-stack-server");
	}

	@Override
	protected RedisConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new RedisDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link RedisConnectionDetails} backed by a {@code Redis} {@link RunningService}.
	 */
	static class RedisDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements RedisConnectionDetails {

		private final String uri;

		RedisDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.uri = "redis://" + service.host() + ":" + service.ports().get(REDIS_PORT);
		}

		@Override
		public String getUri() {
			return this.uri;
		}

	}

}
