package org.springframework.ai.chat.prompt.transformer;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Transforms the PromptContext by retrieving documents from a VectorStore
 */
public class VectorStoreRetriever implements PromptTransformer {

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
		for (Document document : documents) {
			promptContext.addData(document);
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
