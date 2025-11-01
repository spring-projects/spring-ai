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

package org.springframework.ai.model.chat.memory.repository.mongo.autoconfigure;

import org.springframework.ai.chat.memory.repository.mongo.MongoChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Spring Boot autoconfiguration for {@link MongoChatMemoryRepository}.
 *
 * @author Łukasz Jernaś
 * @since 1.1.0
 */
@AutoConfiguration(after = MongoDataAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@EnableConfigurationProperties(MongoChatMemoryProperties.class)
public class MongoChatMemoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	MongoChatMemoryRepository chatMemoryRepository(MongoTemplate mongoTemplate) {
		return MongoChatMemoryRepository.builder().mongoTemplate(mongoTemplate).build();
	}

}
