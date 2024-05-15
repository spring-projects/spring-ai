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

package org.springframework.ai.chat.prompt.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Transforms the PromptContext by retrieving documents from a VectorStore
 */
public class VectorStoreRetriever implements PromptTransformer {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final VectorStore vectorStore;

	private final SearchRequest searchRequest;

	public VectorStoreRetriever(VectorStore vectorStore, SearchRequest searchRequest) {
		this.vectorStore = vectorStore;
		this.searchRequest = searchRequest;
	}

	public VectorStore getVectorStore() {
		return vectorStore;
	}

	public SearchRequest getSearchRequest() {
		return searchRequest;
	}

	@Override
	public PromptContext transform(PromptContext promptContext) {
		List<Message> instructions = promptContext.getPrompt().getInstructions();
		String userMessage = instructions.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(m -> m.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		List<Document> documents = vectorStore.similaritySearch(searchRequest.withQuery(userMessage));

		logger.info("Retrieved {} documents for user message {}", documents.size(), userMessage);
		for (Document document : documents) {
			var content = new Document(document.getContent(), document.getMetadata());
			// content.getMetadata().put(TransformerContentType.DOMAIN_DATA, true);
			promptContext.addData(content);
		}
		return promptContext;
	}

	@Override
	public String toString() {
		return "VectorStoreRetriever{" + "vectorStore=" + vectorStore + ", searchRequest=" + searchRequest + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof VectorStoreRetriever that))
			return false;
		return Objects.equals(vectorStore, that.vectorStore) && Objects.equals(searchRequest, that.searchRequest);
	}

	@Override
	public int hashCode() {
		return Objects.hash(vectorStore, searchRequest);
	}

}
