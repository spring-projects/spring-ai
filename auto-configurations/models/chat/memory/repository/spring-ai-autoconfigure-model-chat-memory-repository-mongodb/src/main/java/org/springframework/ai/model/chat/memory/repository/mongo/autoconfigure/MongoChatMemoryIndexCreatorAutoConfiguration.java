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

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.repository.mongo.Conversation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Class responsible for creating proper MongoDB indices for the ChatMemory. Creates a
 * main index on the conversationId and timestamp fields, and a TTL index on the timestamp
 * field if the TTL is set in properties.
 *
 * @author Łukasz Jernaś
 * @see MongoChatMemoryProperties
 * @since 1.1.0
 */
@AutoConfiguration
@ConditionalOnProperty(value = "spring.ai.chat.memory.repository.mongo.create-indices", havingValue = "true")
public class MongoChatMemoryIndexCreatorAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(MongoChatMemoryIndexCreatorAutoConfiguration.class);

	private final MongoTemplate mongoTemplate;

	private final MongoChatMemoryProperties mongoChatMemoryProperties;

	public MongoChatMemoryIndexCreatorAutoConfiguration(final MongoTemplate mongoTemplate,
			final MongoChatMemoryProperties mongoChatMemoryProperties) {
		this.mongoTemplate = mongoTemplate;
		this.mongoChatMemoryProperties = mongoChatMemoryProperties;
	}

	/**
	 * Initializes MongoDB indices after application context refresh.
	 */
	@EventListener(ContextRefreshedEvent.class)
	public void initIndicesAfterStartup() {
		logger.info("Creating MongoDB indices for ChatMemory");
		// Create a main index
		createMainIndex();
		createOrUpdateTtlIndex();
	}

	private void createMainIndex() {
		var indexOps = this.mongoTemplate.indexOps(Conversation.class);
		var index = new Index().on("conversationId", Sort.Direction.ASC).on("timestamp", Sort.Direction.DESC);

		// Use reflection to handle API differences across Spring Data MongoDB versions
		createIndexSafely(indexOps, index);
	}

	private void createOrUpdateTtlIndex() {
		if (!this.mongoChatMemoryProperties.getTtl().isZero()) {
			var indexOps = this.mongoTemplate.indexOps(Conversation.class);
			// Check for existing TTL index
			indexOps.getIndexInfo().forEach(idx -> {
				if (idx.getExpireAfter().isPresent()
						&& !idx.getExpireAfter().get().equals(this.mongoChatMemoryProperties.getTtl())) {
					logger.warn("Dropping existing TTL index, because TTL is different");
					indexOps.dropIndex(idx.getName());
				}
			});
			// Use reflection to handle API differences across Spring Data MongoDB
			// versions
			createIndexSafely(indexOps,
					new Index().on("timestamp", Sort.Direction.ASC).expire(this.mongoChatMemoryProperties.getTtl()));
		}
	}

	/**
	 * Creates an index using reflection to handle API changes across different Spring
	 * Data MongoDB versions:
	 * <ul>
	 * <li>Spring Data MongoDB 4.2.x - 4.4.x: only {@code ensureIndex(IndexDefinition)} is
	 * available.</li>
	 * <li>Spring Data MongoDB 4.5.x+: {@code createIndex(IndexDefinition)} is the new
	 * API, {@code ensureIndex} is deprecated.</li>
	 * </ul>
	 * @param indexOps the IndexOperations instance
	 * @param index the index definition
	 * @throws IllegalStateException if neither method is available or invocation fails
	 */
	private void createIndexSafely(final IndexOperations indexOps, final IndexDefinition index) {
		try {
			// Try new API (Spring Data MongoDB 4.5.x+)
			Method method = IndexOperations.class.getMethod("createIndex", IndexDefinition.class);
			method.invoke(indexOps, index);
			logger.debug("Created index using createIndex() method");
		}
		catch (NoSuchMethodException createIndexNotFound) {
			// Fall back to old API (Spring Data MongoDB 4.2.x - 4.4.x)
			try {
				Method method = IndexOperations.class.getMethod("ensureIndex", IndexDefinition.class);
				method.invoke(indexOps, index);
				logger.debug("Created index using ensureIndex() method");
			}
			catch (NoSuchMethodException ensureIndexNotFound) {
				throw new IllegalStateException(
						"Neither createIndex() nor ensureIndex() method found on IndexOperations. "
								+ "This may indicate an unsupported Spring Data MongoDB version.",
						ensureIndexNotFound);
			}
			catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("Failed to invoke ensureIndex() method", ex);
			}
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to invoke createIndex() method", ex);
		}
	}

}
