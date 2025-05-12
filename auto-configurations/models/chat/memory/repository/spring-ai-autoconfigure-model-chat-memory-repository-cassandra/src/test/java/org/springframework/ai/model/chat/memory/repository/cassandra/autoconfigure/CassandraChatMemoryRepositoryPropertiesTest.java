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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.cassandra.CassandraChatMemoryRepositoryConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
class CassandraChatMemoryRepositoryPropertiesTest {

	@Test
	void defaultValues() {
		var props = new CassandraChatMemoryRepositoryProperties();
		assertThat(props.getKeyspace()).isEqualTo(CassandraChatMemoryRepositoryConfig.DEFAULT_KEYSPACE_NAME);
		assertThat(props.getTable()).isEqualTo(CassandraChatMemoryRepositoryConfig.DEFAULT_TABLE_NAME);
		assertThat(props.getAssistantColumn())
			.isEqualTo(CassandraChatMemoryRepositoryConfig.DEFAULT_ASSISTANT_COLUMN_NAME);
		assertThat(props.getUserColumn()).isEqualTo(CassandraChatMemoryRepositoryConfig.DEFAULT_USER_COLUMN_NAME);
		assertThat(props.getTimeToLive()).isNull();
		assertThat(props.isInitializeSchema()).isTrue();
	}

	@Test
	void customValues() {
		var props = new CassandraChatMemoryRepositoryProperties();
		props.setKeyspace("my_keyspace");
		props.setTable("my_table");
		props.setAssistantColumn("my_assistant_column");
		props.setUserColumn("my_user_column");
		props.setTimeToLive(Duration.ofDays(1));
		props.setInitializeSchema(false);

		assertThat(props.getKeyspace()).isEqualTo("my_keyspace");
		assertThat(props.getTable()).isEqualTo("my_table");
		assertThat(props.getAssistantColumn()).isEqualTo("my_assistant_column");
		assertThat(props.getUserColumn()).isEqualTo("my_user_column");
		assertThat(props.getTimeToLive()).isEqualTo(Duration.ofDays(1));
		assertThat(props.isInitializeSchema()).isFalse();
	}

}
