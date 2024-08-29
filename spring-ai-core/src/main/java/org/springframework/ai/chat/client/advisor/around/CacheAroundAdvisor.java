/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.chat.client.advisor.around;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.CollectionUtils;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class CacheAroundAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

	private final VectorStore vectorStore;

	private static final String DOCUMENT_METADATA_ADVISOR_CACHE_TAG = "advisorCacheDocument";

	private static final String DOCUMENT_METADATA_ADVISOR_CACHE_RESPONSE = "advisorCacheResponse";

	public CacheAroundAdvisor(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public ChatResponse aroundCall(AdvisedRequest advisedRequest, Map<String, Object> adviceContext,
			AroundAdvisorChain chain) {

		var cachedResponseOption = getCacheEntry(advisedRequest, adviceContext);
		if (cachedResponseOption.isPresent()) {
			return cachedResponseOption.get();
		}

		ChatResponse chatResponse = chain.nextAroundCall(advisedRequest, adviceContext);

		saveCacheEntry(advisedRequest.userText(), chatResponse);

		return chatResponse;
	}

	@Override
	public Flux<ChatResponse> aroundStream(AdvisedRequest advisedRequest, Map<String, Object> adviceContext,
			AroundAdvisorChain chain) {

		var cachedResponseOption = getCacheEntry(advisedRequest, adviceContext);
		if (cachedResponseOption.isPresent()) {
			return Flux.just(cachedResponseOption.get());
		}

		Flux<ChatResponse> fluxChatResponse = chain.nextAroundStream(advisedRequest, adviceContext);

		return new MessageAggregator().aggregate(fluxChatResponse, chatResponse -> {
			saveCacheEntry(advisedRequest.userText(), chatResponse);
		});
	}

	private void saveCacheEntry(String userQuestion, ChatResponse chatResponse) {
		List<Message> assistantMessages = chatResponse.getResults().stream().map(g -> (Message) g.getOutput()).toList();
		if (!CollectionUtils.isEmpty(assistantMessages)) {
			this.vectorStore.add(toDocuments(userQuestion, assistantMessages));
		}
	}

	private Optional<ChatResponse> getCacheEntry(AdvisedRequest advisedRequest, Map<String, Object> adviceContext) {

		// TODO: convert into pompty first or at least materialize the user params.
		String userText = advisedRequest.userText();

		// @formatter:off
		var searchRequest = SearchRequest.query(userText)
			.withSimilarityThreshold(0.95)
			.withTopK(1)
			.withFilterExpression("'"+ DOCUMENT_METADATA_ADVISOR_CACHE_TAG + "' == 'true'");
		// @formatter:on

		List<Document> doc = vectorStore.similaritySearch(searchRequest);

		// return cached response
		return CollectionUtils.isEmpty(doc) ? Optional.empty() : Optional.of(fromDocument(doc.get(0)));
	}

	private ChatResponse fromDocument(Document doc) {

		if (!doc.getMetadata().containsKey(DOCUMENT_METADATA_ADVISOR_CACHE_RESPONSE)) {
			throw new IllegalStateException("The document is missing the cache response metadata!");
		}
		String cachedResponse = "" + doc.getMetadata().get(DOCUMENT_METADATA_ADVISOR_CACHE_RESPONSE);

		return ChatResponse.builder()
			.withGenerations(List.of(new Generation(new AssistantMessage(cachedResponse, Map.of()),
					ChatGenerationMetadata.from("STOP", null))))
			.build();
	}

	private List<Document> toDocuments(String userQuestion, List<Message> messages) {

		List<Document> docs = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				var metadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
				metadata.put(DOCUMENT_METADATA_ADVISOR_CACHE_TAG, "true");
				metadata.put(DOCUMENT_METADATA_ADVISOR_CACHE_RESPONSE, message.getContent());
				// TODO: Pehaps we need to serialize the message metadata to the document

				return new Document(userQuestion, metadata);

			})
			.toList();

		return docs;
	}

}
