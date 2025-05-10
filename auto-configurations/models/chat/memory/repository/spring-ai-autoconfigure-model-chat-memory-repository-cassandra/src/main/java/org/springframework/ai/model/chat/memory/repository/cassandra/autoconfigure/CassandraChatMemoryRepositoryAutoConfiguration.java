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

package org.springframework.ai.model.chat.memory.repository.cassandra.autoconfigure;

import com.datastax.oss.driver.api.core.CqlSession;

import org.springframework.ai.chat.memory.cassandra.CassandraChatMemoryRepositoryConfig;
import org.springframework.ai.chat.memory.cassandra.CassandraChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link CassandraChatMemoryRepository}.
 *
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@AutoConfiguration(after = CassandraAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ CassandraChatMemoryRepository.class, CqlSession.class })
@EnableConfigurationProperties(CassandraChatMemoryRepositoryProperties.class)
public class CassandraChatMemoryRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public CassandraChatMemoryRepository cassandraChatMemoryRepository(
			CassandraChatMemoryRepositoryProperties properties, CqlSession cqlSession) {

		var builder = CassandraChatMemoryRepositoryConfig.builder().withCqlSession(cqlSession);

		builder = builder.withKeyspaceName(properties.getKeyspace())
			.withTableName(properties.getTable())
			.withAssistantColumnName(properties.getAssistantColumn())
			.withUserColumnName(properties.getUserColumn());

		if (!properties.isInitializeSchema()) {
			builder = builder.disallowSchemaChanges();
		}
		if (null != properties.getTimeToLive()) {
			builder = builder.withTimeToLive(properties.getTimeToLive());
		}

		return CassandraChatMemoryRepository.create(builder.build());
	}

}
