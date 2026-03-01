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

package org.springframework.ai.chat.memory.repository.arcadedb.autoconfigure;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;

import org.springframework.ai.chat.memory.repository.arcadedb.ArcadeDBChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for ArcadeDB ChatMemoryRepository.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ArcadeDBChatMemoryRepository.class)
@EnableConfigurationProperties(ArcadeDBChatMemoryRepositoryProperties.class)
public class ArcadeDBChatMemoryRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(
			prefix = ArcadeDBChatMemoryRepositoryProperties.CONFIG_PREFIX,
			name = "database-path")
	public ArcadeDBChatMemoryRepository arcadeDBChatMemoryRepository(
			ArcadeDBChatMemoryRepositoryProperties properties) {
		ArcadeDBChatMemoryRepository.Builder builder = ArcadeDBChatMemoryRepository
				.builder()
				.sessionTypeName(properties.getSessionTypeName())
				.messageTypeName(properties.getMessageTypeName())
				.edgeTypeName(properties.getEdgeTypeName());

		builder.databasePath(properties.getDatabasePath());

		return builder.build();
	}

}
