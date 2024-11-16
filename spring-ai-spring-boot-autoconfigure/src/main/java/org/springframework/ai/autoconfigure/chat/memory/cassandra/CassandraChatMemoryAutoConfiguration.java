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

package org.springframework.ai.autoconfigure.chat.memory.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;

import org.springframework.ai.chat.memory.CassandraChatMemory;
import org.springframework.ai.chat.memory.CassandraChatMemoryConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link CassandraChatMemory}.
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@AutoConfiguration(after = CassandraAutoConfiguration.class)
@ConditionalOnClass({ CassandraChatMemory.class, CqlSession.class })
@EnableConfigurationProperties(CassandraChatMemoryProperties.class)
public class CassandraChatMemoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public CassandraChatMemory chatMemory(CassandraChatMemoryProperties properties, CqlSession cqlSession) {

		var builder = CassandraChatMemoryConfig.builder().withCqlSession(cqlSession);

		builder = builder.withKeyspaceName(properties.getKeyspace())
			.withTableName(properties.getTable())
			.withAssistantColumnName(properties.getAssistantColumn())
			.withUserColumnName(properties.getUserColumn());

		if (!properties.isInitializeSchema()) {
			builder = builder.disallowSchemaChanges();
		}
		if (null != properties.getTimeToLiveSeconds()) {
			builder = builder.withTimeToLive(properties.getTimeToLiveSeconds());
		}

		return CassandraChatMemory.create(builder.build());
	}

}
