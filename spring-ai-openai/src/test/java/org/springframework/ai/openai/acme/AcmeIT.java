package org.springframework.ai.openai.acme;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.client.AiResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.retriever.VectorStoreRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.InMemoryVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class AcmeIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(AcmeIT.class);

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	@Value("classpath:/prompts/acme/system-qa.st")
	private Resource systemBikePrompt;

	@Autowired
	private OpenAiEmbeddingClient embeddingClient;

	@Autowired
	private OpenAiClient aiClient;

	@Test
	void beanTest() {
		assertThat(bikesResource).isNotNull();
		assertThat(embeddingClient).isNotNull();
		assertThat(aiClient).isNotNull();
	}

	// @Test
	void acmeChain() {

		// Step 1 - load documents
		JsonReader jsonReader = new JsonReader(bikesResource, "name", "price", "shortDescription", "description");

		var textSplitter = new TokenTextSplitter();

		// Step 2 - Create embeddings and save to vector store

		logger.info("Creating Embeddings...");
		VectorStore vectorStore = new InMemoryVectorStore(embeddingClient);

		vectorStore.accept(textSplitter.apply(jsonReader.get()));

		// Now user query

		// This will be wrapped up in a chain
		VectorStoreRetriever vectorStoreRetriever = new VectorStoreRetriever(vectorStore);

		logger.info("Retrieving relevant documents");
		String userQuery = "What bike is good for city commuting?";

		// "Tell me more about the bike 'The SonicRide 8S'" ;
		// "How much does the SonicRide 8S cost?";

		// Eventually include metadata in query.
		List<Document> similarDocuments = vectorStoreRetriever.retrieve(userQuery);
		logger.info(String.format("Found %s relevant documents.", similarDocuments.size()));

		// Try the case where not product was specified, so query over whatever docs might
		// be relevant.

		Message systemMessage = getSystemMessage(similarDocuments);
		UserMessage userMessage = new UserMessage(userQuery);

		// Create the prompt ad-hoc for now, need to put in system message and user
		// message via ChatPromptTemplate or some other message building mechanic;
		logger.info("Asking AI model to reply to question.");
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		logger.info("AI responded.");
		AiResponse response = aiClient.generate(prompt);

		evaluateQuestionAndAnswer(userQuery, response, true);
	}

	private Message getSystemMessage(List<Document> similarDocuments) {

		String documents = similarDocuments.stream().map(entry -> entry.getContent()).collect(Collectors.joining("\n"));

		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBikePrompt);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("documents", documents));
		return systemMessage;

	}

}
