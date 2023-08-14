package org.springframework.ai.openai.acme;

import org.junit.jupiter.api.Test;
import org.springframework.ai.core.document.Document;
import org.springframework.ai.core.llm.LLMResponse;
import org.springframework.ai.core.llm.LlmClient;
import org.springframework.ai.core.loader.impl.JsonLoader;
import org.springframework.ai.core.prompt.ChatPromptTemplate;
import org.springframework.ai.core.prompt.Prompt;
import org.springframework.ai.core.prompt.PromptTemplate;
import org.springframework.ai.core.prompt.messages.ChatMessage;
import org.springframework.ai.core.prompt.messages.SystemMessage;
import org.springframework.ai.core.prompt.messages.UserMessage;
import org.springframework.ai.core.retriever.impl.VectorStoreRetriever;
import org.springframework.ai.core.vectorstore.VectorStore;
import org.springframework.ai.core.vectorstore.impl.InMemoryVectorStore;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AcmeIntegrationTest {

	@Value("classpath:bikes.json")
	private Resource resource;

	@Autowired
	private OpenAiEmbeddingClient embeddingClient;

	@Autowired
	private LlmClient llmClient;

	@Test
	void beanTest() {
		assertThat(resource).isNotNull();
		assertThat(embeddingClient).isNotNull();
		assertThat(llmClient).isNotNull();
	}

	void acmeChain() {

		// Step 1 - load documents
		JsonLoader jsonLoader = new JsonLoader("description", resource);
		List<Document> documents = jsonLoader.load();

		// Step 2 - Create embeddings and save to vector store

		VectorStore vectorStore = new InMemoryVectorStore(embeddingClient);

		vectorStore.add(documents);

		// Now user query

		// This will be wrapped up in a chain

		VectorStoreRetriever vectorStoreRetriever = new VectorStoreRetriever(vectorStore);

		String userQuery = "What bike is good for city commuting?";
		List<Document> similarDocuments = vectorStoreRetriever.retrieve(userQuery);

		// Try the case where not product was specified, so query over whatever docs might
		// be releveant.

		SystemMessage systemMessage = getSystemMessage(similarDocuments);
		UserMessage userMessage = new UserMessage(userQuery);

		// Create the prompt ad-hoc for now, need to put in system message and user
		// message via ChatPromptTemplate or some other message building mechanic
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		LLMResponse response = llmClient.generate(prompt);

		// Chain
		// qa = new ConversationalRetrievalChain(llmClient, userPromptTemplate,
		// vectorStoreRetriever, )
	}

	private SystemMessage getSystemMessage(List<Document> similarDocuments) {

		// Would need to figure out which of the documenta metadata fields to add, from
		// the loader, now just the 'full description.'

		String systemMessageText = similarDocuments.stream()
			.map(entry -> entry.getContent())
			.collect(Collectors.joining("\n"));

		return new SystemMessage(systemMessageText);

	}

}
