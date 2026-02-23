/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.acme;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
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
	private OpenAiEmbeddingModel embeddingModel;

	@Autowired
	private OpenAiChatModel chatModel;

	@Test
	void beanTest() {
		assertThat(this.bikesResource).isNotNull();
		assertThat(this.embeddingModel).isNotNull();
		assertThat(this.chatModel).isNotNull();
	}

	// @Test
	void acmeChain() {

		// Step 1 - load documents
		JsonReader jsonReader = new JsonReader(this.bikesResource, "name", "price", "shortDescription", "description");

		var textSplitter = TokenTextSplitter.builder().build();

		// Step 2 - Create embeddings and save to vector store

		logger.info("Creating Embeddings...");
		VectorStore vectorStore = SimpleVectorStore.builder(this.embeddingModel).build();

		vectorStore.accept(textSplitter.apply(jsonReader.get()));

		// Now user query

		logger.info("Retrieving relevant documents");
		String userQuery = "What bike is good for city commuting?";

		// "Tell me more about the bike 'The SonicRide 8S'" ;
		// "How much does the SonicRide 8S cost?";

		// Eventually include metadata in query.
		List<Document> similarDocuments = vectorStore.similaritySearch(userQuery);
		logger.info(String.format("Found %s relevant documents.", similarDocuments.size()));

		// Try the case where not product was specified, so query over whatever docs might
		// be relevant.

		Message systemMessage = getSystemMessage(similarDocuments);
		UserMessage userMessage = new UserMessage(userQuery);

		// Create the prompt ad-hoc for now, need to put in system message and user
		// message via ChatPromptTemplate or some other message building mechanic;
		logger.info("Asking AI generative to reply to question.");
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		logger.info("AI responded.");
		ChatResponse response = this.chatModel.call(prompt);

		evaluateQuestionAndAnswer(userQuery, response, true);
	}

	private Message getSystemMessage(List<Document> similarDocuments) {

		String documents = similarDocuments.stream()
			.map(entry -> entry.getText())
			.collect(Collectors.joining(System.lineSeparator()));

		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemBikePrompt);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("documents", documents));
		return systemMessage;

	}

}
