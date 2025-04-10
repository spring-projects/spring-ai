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

package org.springframework.ai.model.chat.memory.neo4j.autoconfigure;

import org.neo4j.driver.Driver;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemory;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link Neo4jChatMemory}.
 *
 * @author Enrico Rampazzo
 * @since 1.0.0
 */
@AutoConfiguration(after = Neo4jAutoConfiguration.class)
@ConditionalOnClass({ Neo4jChatMemory.class, Driver.class })
@EnableConfigurationProperties(Neo4jChatMemoryProperties.class)
public class Neo4jChatMemoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Neo4jChatMemory chatMemory(Neo4jChatMemoryProperties properties, Driver driver) {

		var builder = Neo4jChatMemoryConfig.builder()
			.withMediaLabel(properties.getMediaLabel())
			.withMessageLabel(properties.getMessageLabel())
			.withMetadataLabel(properties.getMetadataLabel())
			.withSessionLabel(properties.getSessionLabel())
			.withToolCallLabel(properties.getToolCallLabel())
			.withToolResponseLabel(properties.getToolResponseLabel())
			.withDriver(driver);

		return Neo4jChatMemory.create(builder.build());
	}

}
