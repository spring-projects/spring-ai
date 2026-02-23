/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.repository.elasticsearch.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticSearchChatMemoryRepositoryConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ElasticSearchChatMemoryRepositoryProperties}.
 *
 * @author Fu Jian
 * @since 1.1.0
 */
class ElasticSearchChatMemoryRepositoryPropertiesTest {

	@Test
	void defaultValues() {
		var props = new ElasticSearchChatMemoryRepositoryProperties();
		assertThat(props.getIndexName()).isEqualTo(ElasticSearchChatMemoryRepositoryConfig.DEFAULT_INDEX_NAME);
	}

	@Test
	void customValues() {
		var props = new ElasticSearchChatMemoryRepositoryProperties();
		props.setIndexName("custom_chat_memory");

		assertThat(props.getIndexName()).isEqualTo("custom_chat_memory");
	}

}
