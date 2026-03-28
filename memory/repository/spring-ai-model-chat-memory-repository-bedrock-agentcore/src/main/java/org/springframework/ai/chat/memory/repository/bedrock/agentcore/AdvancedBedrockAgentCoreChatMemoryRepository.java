/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.memory.repository.bedrock.agentcore;

import java.util.List;

import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

/**
 * Bedrock AgentCore-specific extended interface for {@link ChatMemoryRepository} that
 * provides access to <strong>long-term memory</strong> operations.
 *
 * <p>
 * Bedrock AgentCore separates memory into two layers:
 * <ul>
 * <li><strong>Short-term (Events API)</strong>: raw conversation events stored per
 * session. The base {@link ChatMemoryRepository} operations ({@code saveAll},
 * {@code findByConversationId}, {@code deleteByConversationId}) map to this layer.</li>
 * <li><strong>Long-term (MemoryRecords API)</strong>: structured memories extracted from
 * conversations by Bedrock's memory strategies. This interface adds operations for
 * semantic retrieval, listing, and deletion of these records.</li>
 * </ul>
 *
 * <p>
 * The {@code memoryId} is always taken from the underlying
 * {@link BedrockAgentCoreChatMemoryConfig} and does not need to be supplied by the
 * caller.
 *
 * <p>
 * For multi-actor (e.g. per-user) scenarios, use the overloads that take an explicit
 * {@code actorId}. The {@link ChatMemoryRepository} methods delegate to those overloads
 * using {@link BedrockAgentCoreChatMemoryConfig#getActorId()}.
 *
 * @author Chaemin Lee
 * @see BedrockAgentCoreChatMemoryRepository
 * @see BedrockAgentCoreChatMemoryConfig
 */
public interface AdvancedBedrockAgentCoreChatMemoryRepository extends ChatMemoryRepository {

	/**
	 * Lists conversation ids (Bedrock session ids) for the given actor.
	 *
	 * <p>
	 * Uses the Bedrock SDK paginator ({@code listSessionsPaginator}) to automatically
	 * traverse all pages. The SDK fetches pages of up to 20 items by default, but all
	 * pages are consumed transparently and the full result set is returned.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @return session ids, never {@code null}
	 */
	List<String> findConversationIds(String actorId);

	/**
	 * Loads all messages for a conversation scoped to the given actor.
	 *
	 * <p>
	 * Uses the Bedrock SDK paginator ({@code listEventsPaginator}) to automatically
	 * traverse all pages. The SDK fetches pages of up to 20 items by default, but all
	 * pages are consumed transparently and the full result set is returned.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @param conversationId the conversation id (Bedrock session id)
	 * @return messages in chronological order, never {@code null}
	 */
	List<Message> findByConversationId(String actorId, String conversationId);

	/**
	 * Replaces all messages for a conversation scoped to the given actor.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @param conversationId the conversation id (Bedrock session id)
	 * @param messages the messages to store
	 */
	void saveAll(String actorId, String conversationId, List<Message> messages);

	/**
	 * Deletes all messages for a conversation scoped to the given actor.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @param conversationId the conversation id (Bedrock session id)
	 */
	void deleteByConversationId(String actorId, String conversationId);

	/**
	 * Semantically searches long-term memory records under the namespace.
	 *
	 * <p>
	 * Uses the Bedrock SDK paginator ({@code retrieveMemoryRecordsPaginator}) to
	 * automatically traverse all pages. The SDK fetches pages of up to 20 items by
	 * default, but all pages are consumed transparently and the full result set is
	 * returned.
	 * @param namespace namespace prefix for stored records
	 * @param searchQuery natural-language query (up to 10,000 characters)
	 * @return matching summaries ordered by relevance, never {@code null}
	 */
	List<MemoryRecordSummary> retrieveMemoryRecords(String namespace, String searchQuery);

	/**
	 * Lists long-term memory records under the namespace without semantic search.
	 *
	 * <p>
	 * Uses the Bedrock SDK paginator ({@code listMemoryRecordsPaginator}) to
	 * automatically traverse all pages. The SDK fetches pages of up to 20 items by
	 * default, but all pages are consumed transparently and the full result set is
	 * returned.
	 * @param namespace namespace prefix
	 * @return summaries, never {@code null}
	 */
	List<MemoryRecordSummary> listMemoryRecords(String namespace);

	/**
	 * Deletes a specific long-term memory record by its ID.
	 * @param memoryRecordId the memory record identifier
	 */
	void deleteMemoryRecord(String memoryRecordId);

}
