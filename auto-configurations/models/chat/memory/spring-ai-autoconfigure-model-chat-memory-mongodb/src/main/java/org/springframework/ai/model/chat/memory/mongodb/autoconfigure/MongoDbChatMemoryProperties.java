/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.mongodb.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @author Łukasz Jernaś
 * @since 1.0.0
 */
@ConfigurationProperties(MongoDbChatMemoryProperties.CONFIG_PREFIX)
public class MongoDbChatMemoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.mongodb";

	/**
	 * If the indexes should be automatically created on app startup. Note: Changing the
	 * TTL value will drop the TTL index and recreate it.
	 */
	private boolean createIndexes = false;

	/**
	 * The time to live (TTL) for the conversation documents in the database. The default
	 * value is 0, which means that the documents will not expire.
	 */
	private Duration ttl = Duration.ZERO;

	public Duration getTtl() {
		return ttl;
	}

	public void setTtl(Duration ttl) {
		this.ttl = ttl;
	}

	public boolean isCreateIndexes() {
		return createIndexes;
	}

	public void setCreateIndexes(boolean createIndexes) {
		this.createIndexes = createIndexes;
	}

}
