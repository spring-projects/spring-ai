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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.repository.mongo.Conversation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Class responsible for creating MongoDB proper indices for the ChatMemory. Creates a
 * main index on the conversationId and timestamp fields, and a TTL index on the timestamp
 * field if the TTL is set in properties.
 *
 * @author Łukasz Jernaś
 * @see MongoChatMemoryProperties
 * @since 1.1.0
 */
@Component
@ConditionalOnProperty(value = "spring.ai.chat.memory.repository.mongo.create-indices", havingValue = "true")
public class MongoChatMemoryIndexCreator {

	private static final Logger logger = LoggerFactory.getLogger(MongoChatMemoryIndexCreator.class);

	private final MongoTemplate mongoTemplate;

	private final MongoChatMemoryProperties mongoChatMemoryProperties;

	public MongoChatMemoryIndexCreator(MongoTemplate mongoTemplate,
			MongoChatMemoryProperties mongoChatMemoryProperties) {
		this.mongoTemplate = mongoTemplate;
		this.mongoChatMemoryProperties = mongoChatMemoryProperties;
	}

	@EventListener(ContextRefreshedEvent.class)
	public void initIndicesAfterStartup() {
		logger.info("Creating MongoDB indices for ChatMemory");
		// Create a main index
		this.mongoTemplate.indexOps(Conversation.class)
			.createIndex(new Index().on("conversationId", Sort.Direction.ASC).on("timestamp", Sort.Direction.DESC));

		createOrUpdateTtlIndex();
	}

	private void createOrUpdateTtlIndex() {
		if (!this.mongoChatMemoryProperties.getTtl().isZero()) {
			// Check for existing TTL index
			this.mongoTemplate.indexOps(Conversation.class).getIndexInfo().forEach(idx -> {
				if (idx.getExpireAfter().isPresent()
						&& !idx.getExpireAfter().get().equals(this.mongoChatMemoryProperties.getTtl())) {
					logger.warn("Dropping existing TTL index, because TTL is different");
					this.mongoTemplate.indexOps(Conversation.class).dropIndex(idx.getName());
				}
			});
			this.mongoTemplate.indexOps(Conversation.class)
				.createIndex(new Index().on("timestamp", Sort.Direction.ASC)
					.expire(this.mongoChatMemoryProperties.getTtl()));
		}
	}

}
