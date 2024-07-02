package org.springframework.ai.wenxin.acme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.AssistantPromptTemplate;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.wenxin.WenxinChatModel;
import org.springframework.ai.wenxin.WenxinEmbeddingModel;
import org.springframework.ai.wenxin.WenxinTestConfiguration;
import org.springframework.ai.wenxin.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WenxinTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "WENXIN_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WENXIN_SECRET_KEY", matches = ".+")
public class AcmeIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(AcmeIT.class);

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	@Value("classpath:/prompts/acme/system-qa.st")
	private Resource systemBikePrompt;

	@Autowired
	private WenxinEmbeddingModel embeddingModel;

	@Autowired
	private WenxinChatModel chatModel;

	@Test
	void beanTest() {
		assertThat(bikesResource).isNotNull();
		assertThat(embeddingModel).isNotNull();
		assertThat(chatModel).isNotNull();
	}

	@Test
	void acmeChain() throws IOException {
		JsonReader jsonReader = new JsonReader(bikesResource, "name", "price", "shortDescription", "description");

		var testSplitter = new TokenTextSplitter();

		logger.info("Creating Embeddings...");

		VectorStore vectorStore = new SimpleVectorStore(embeddingModel);
		vectorStore.accept(testSplitter.apply(jsonReader.get()));

		logger.info("Retrieving relevant documents");
		String userQuery = "What bike is good for city commuting?";

		List<Document> similarDocuments = vectorStore.similaritySearch(userQuery);

		logger.info(String.format("Found %s relevant documents.", similarDocuments.size()));

		Message assistantMessage = getAssistantMessage(similarDocuments);
		UserMessage userMessage = new UserMessage(userQuery);

		logger.info("Asking AI generative to reply to question.");
		Prompt prompt = new Prompt(List.of(userMessage, assistantMessage, userMessage));
		logger.info("AI responded.");
		ChatResponse response = chatModel.call(prompt);

		evaluateQuestionAndAnswer(userQuery, response, true);
	}

	private Message getAssistantMessage(List<Document> similarDocuments) {

		String documents = similarDocuments.stream()
			.map(entry -> entry.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		AssistantPromptTemplate systemPromptTemplate = new AssistantPromptTemplate(systemBikePrompt);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("documents", documents));
		return systemMessage;

	}

}
