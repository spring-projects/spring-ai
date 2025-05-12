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

package org.springframework.ai.model.chat.memory.repository.neo4j.autoconfigure;

import org.neo4j.driver.Driver;

import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryRepositoryConfig;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link Neo4jChatMemoryRepository}.
 *
 * @author Enrico Rampazzo
 * @since 1.0.0
 */
@AutoConfiguration(after = Neo4jAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ Neo4jChatMemoryRepository.class, Driver.class })
@EnableConfigurationProperties(Neo4jChatMemoryRepositoryProperties.class)
public class Neo4jChatMemoryRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Neo4jChatMemoryRepository neo4jChatMemoryRepository(Neo4jChatMemoryRepositoryProperties properties,
			Driver driver) {

		var builder = Neo4jChatMemoryRepositoryConfig.builder()
			.withMediaLabel(properties.getMediaLabel())
			.withMessageLabel(properties.getMessageLabel())
			.withMetadataLabel(properties.getMetadataLabel())
			.withSessionLabel(properties.getSessionLabel())
			.withToolCallLabel(properties.getToolCallLabel())
			.withToolResponseLabel(properties.getToolResponseLabel())
			.withDriver(driver);

		return new Neo4jChatMemoryRepository(builder.build());
	}

}
