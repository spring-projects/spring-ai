/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class VectorStoreChatMemoryRetriever implements PromptTransformer {

	private final VectorStore vectorStore;

	private final int topK;

	/**
	 * Additional metadata to be assigned to the retrieved history messages.
	 */
	private final Map<String, Object> additionalMetadata;

	public VectorStoreChatMemoryRetriever(VectorStore vectorStore, int topK) {
		this(vectorStore, topK, Map.of());
	}

	public VectorStoreChatMemoryRetriever(VectorStore vectorStore, int topK, Map<String, Object> additionalMetadata) {
		this.vectorStore = vectorStore;
		this.topK = topK;
		this.additionalMetadata = additionalMetadata;
	}

	@Override
	public PromptContext transform(PromptContext promptContext) {
		List<Content> updatedContents = new ArrayList<>(
				promptContext.getContents() != null ? promptContext.getContents() : List.of());

		String query = promptContext.getPrompt()
			.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(m -> m.getContent())
			.collect(Collectors.joining());

		var searchRequest = SearchRequest.query(query)
			.withTopK(this.topK)
			.withFilterExpression(
					TransformerContentType.CONVERSATION_ID + "=='" + promptContext.getConversationId() + "'");

		List<Document> documents = this.vectorStore.similaritySearch(searchRequest);

		if (!CollectionUtils.isEmpty(documents)) {
			documents.forEach(d -> {
				d.getMetadata().putAll(this.additionalMetadata);
				d.getMetadata().put(TransformerContentType.MEMORY, true);
			});
			updatedContents.addAll(documents);
		}

		return PromptContext.from(promptContext).withContents(updatedContents).build();
	}

}
